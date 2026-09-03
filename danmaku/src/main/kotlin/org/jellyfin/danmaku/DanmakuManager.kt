package org.jellyfin.danmaku

import android.app.Activity
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.model.IDisplayer
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
            
            // Performance settings
            // Use Choreographer mode (default) for VSync sync
            // updateMethod = 0 is default
        }
    }

    /**
     * Load danmaku data from a parser.
     */
    fun loadDanmaku(parser: BaseDanmakuParser) {
        val view = danmakuView ?: return
        val context = danmakuContext ?: return
        
        view.prepare(parser, context)
        isPrepared = true
        Timber.d("Danmaku loaded")
    }

    /**
     * Start danmaku playback.
     */
    fun start(position: Long = 0) {
        if (!isPrepared) return
        danmakuView?.start(position)
        Timber.d("Danmaku started at position: $position")
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
        if (!isPrepared) return
        danmakuView?.resume()
        Timber.d("Danmaku resumed")
    }

    /**
     * Seek to a specific position.
     */
    fun seekTo(position: Long) {
        danmakuView?.seekTo(position)
        Timber.d("Danmaku seeked to: $position")
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
        Timber.d("Danmaku visibility: $visible")
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
        danmakuView?.release()
        danmakuView = null
        danmakuContext = null
        isPrepared = false
        Timber.d("DanmakuManager released")
    }

    /**
     * Get the current danmaku context for configuration changes.
     */
    fun getContext(): DanmakuContext? = danmakuContext

    /**
     * Update danmaku configuration.
     */
    fun updateConfig(block: DanmakuContext.() -> Unit) {
        danmakuContext?.apply(block)
        // Note: Some config changes require re-rendering
        danmakuView?.forceRender()
    }
}
