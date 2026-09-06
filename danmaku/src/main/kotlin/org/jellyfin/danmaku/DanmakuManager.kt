package org.jellyfin.danmaku

import android.app.Activity
import android.os.Handler
import android.os.Looper
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuTextureView
import timber.log.Timber

/**
 * Manages danmaku lifecycle and configuration for Jellyfin Android TV.
 * Provides a simplified API for integrating DanmakuFlameMaster with the player.
 */
class DanmakuManager(private val activity: Activity) {

    private var danmakuView: DanmakuTextureView? = null
    private var danmakuContext: DanmakuContext? = null
    private var isPrepared = false
    private var isVisible = true

    // DFM prepares asynchronously; start must happen after the prepared() callback
    // otherwise DanmakuView.start() cancels the pending PREPARE message and the
    // render task is never created.
    private var pendingStartPosition = 0L
    private var pendingStartOnPrepared = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Optional diagnostic callback invoked (on the main thread) once the engine is prepared. */
    var onEnginePrepared: (() -> Unit)? = null

    /**
     * Initialize the danmaku view with default TV-optimized configuration.
     */
    fun initialize(view: DanmakuTextureView) {
        danmakuView = view
        danmakuContext = createDefaultContext()
        Timber.d("DanmakuManager initialized")
    }

    /**
     * Create a default DanmakuContext optimized for TV displays.
     */
    private fun createDefaultContext(): DanmakuContext {
        return DanmakuContext.create().apply {
            // TV-optimized settings
            setScaleTextSize(1.2f)                    // Larger text for TV viewing distance
            setMaximumVisibleSizeInScreen(-1)          // Auto-adjust density
            setScrollSpeedFactor(1.2f)                 // Slightly faster for wider TV screens

            // Style settings
            setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3.5f) // Stroke for better readability
            setDanmakuBold(true)                       // Bold text for clarity
        }
    }

    /**
     * Load danmaku data from a parser.
     * Playback starts automatically once the engine reports it is prepared.
     */
    @JvmOverloads
    fun loadDanmaku(parser: BaseDanmakuParser, startPosition: Long = 0) {
        val view = danmakuView ?: return
        val context = danmakuContext ?: return

        pendingStartPosition = startPosition
        pendingStartOnPrepared = true
        view.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                Timber.d("Danmaku engine prepared, starting at %d", pendingStartPosition)
                mainHandler.post { onEnginePrepared?.invoke() }
                if (pendingStartOnPrepared) {
                    view.start(pendingStartPosition)
                    if (!isVisible) view.hide()
                    pendingStartOnPrepared = false
                }
            }

            override fun updateTimer(timer: DanmakuTimer?) = Unit

            override fun danmakuShown(bae: BaseDanmaku?) = Unit

            override fun drawingFinished() = Unit
        })
        view.prepare(parser, context)
        isPrepared = true
        Timber.d("Danmaku load requested")
    }

    /**
     * Start danmaku playback. Safe to call before the engine is prepared:
     * the start is deferred until the prepared callback fires.
     */
    @JvmOverloads
    fun start(position: Long = 0) {
        val view = danmakuView ?: return
        if (pendingStartOnPrepared) {
            // start will happen in the prepared() callback with the latest position
            pendingStartPosition = position
            return
        }
        view.start(position)
        if (!isVisible) view.hide()
        Timber.d("Danmaku started at position: %d", position)
    }

    /**
     * Pause danmaku playback.
     */
    fun pause() {
        danmakuView?.pause()
        Timber.d("Danmaku paused")
    }

    /**
     * Resume danmaku playback.
     */
    fun resume() {
        danmakuView?.resume()
        Timber.d("Danmaku resumed")
    }

    /**
     * Seek to a specific position.
     */
    fun seekTo(position: Long) {
        danmakuView?.seekTo(position)
        Timber.d("Danmaku seeked to: %d", position)
    }

    /**
     * Show/hide danmaku overlay.
     */
    fun setVisible(visible: Boolean) {
        isVisible = visible
        if (visible) {
            danmakuView?.show()
        } else {
            danmakuView?.hide()
        }
        Timber.d("Danmaku visibility: %b", visible)
    }

    /**
     * Toggle danmaku visibility.
     */
    fun toggleVisibility() {
        setVisible(!isVisible)
    }

    /**
     * Check if danmaku is currently visible.
     */
    fun isDanmakuVisible(): Boolean = isVisible

    /**
     * Current danmaku engine timeline position in milliseconds.
     */
    fun getCurrentTime(): Long = danmakuView?.getCurrentTime() ?: 0L

    /**
     * Apply user display settings to the running engine (hot-applied).
     *
     * @param textSize  scaleTextSize factor (1.0 = original XML size)
     * @param speed     scrollSpeedFactor (larger = slower)
     * @param opacity   0..1 (1 = fully opaque)
     * @param area      0..1 fraction of screen height usable by danmaku
     */
    fun applySettings(textSize: Float, speed: Float, opacity: Float, area: Float) {
        val ctx = danmakuContext ?: return
        runCatching {
            ctx.setScaleTextSize(textSize)
            ctx.setScrollSpeedFactor(speed)
            ctx.setDanmakuTransparency(opacity)
            val h = ctx.mDisplayer.height
            if (h > 0) ctx.setDanmakuMargin((h * (1f - area)).toInt())
        }
        Timber.d("Danmaku settings applied: size=%s speed=%s opacity=%s area=%s", textSize, speed, opacity, area)
    }

    /**
     * Aggregated engine diagnostics for the player UI.
     */
    fun getDiagnostics(): String {
        val view = danmakuView ?: return "no-view"
        val ready = runCatching { view.isViewReady() }.getOrDefault(false)
        val prepared = runCatching { view.isPrepared() }.getOrDefault(false)
        val time = runCatching { view.getCurrentTime() }.getOrDefault(-1L)
        val render = runCatching { view.renderDiag }.getOrDefault("")
        var dispDiag = ""
        runCatching {
            val disp = danmakuContext?.displayer
            if (disp is master.flame.danmaku.danmaku.model.android.AndroidDisplayer) {
                dispDiag = " dispW=${disp.width} dispH=${disp.height}"
            }
            val dur = danmakuContext?.mDanmakuFactory?.MAX_Duration_Scroll_Danmaku?.value
            dispDiag += " dur=$dur"
        }
        return "surface=${if (ready) "OK" else "wait"} engine=${if (prepared) "OK" else "wait"} time=$time$dispDiag $render"
    }

    /**
     * Add a single danmaku item (for live danmaku).
     */
    fun addDanmaku(item: BaseDanmaku) {
        danmakuView?.addDanmaku(item)
    }

    /**
     * Clear all danmaku from screen.
     */
    fun clearDanmakus() {
        danmakuView?.clearDanmakusOnScreen()
    }

    /**
     * Release all resources.
     */
    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        danmakuView?.release()
        danmakuView = null
        danmakuContext = null
        isPrepared = false
        pendingStartOnPrepared = false
        Timber.d("DanmakuManager released")
    }

    /**
     * Get the current danmaku context for configuration changes.
     */
    fun getContext(): DanmakuContext? = danmakuContext
}
