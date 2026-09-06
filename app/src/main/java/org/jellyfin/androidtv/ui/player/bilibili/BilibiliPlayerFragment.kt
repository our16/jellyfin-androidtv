package org.jellyfin.androidtv.ui.player.bilibili

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.content
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import master.flame.danmaku.ui.widget.DanmakuTextureView
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.data.repository.danmaku.DanmakuApiRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.playback.PlaybackManager
import org.jellyfin.androidtv.ui.playback.VideoQueueManager
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.util.apiclient.ReportingHelper
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.danmaku.DanmakuDataSource
import org.jellyfin.danmaku.DanmakuManager
import org.jellyfin.danmaku.JellyfinDanmakuParser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Bilibili style TV player: direction keys seek instantly without stopping playback,
 * items play straight away with resume support and danmaku overlay.
 */
class BilibiliPlayerFragment : Fragment(), View.OnKeyListener {
	companion object {
		const val EXTRA_POSITION: String = "position"
		private const val AUTO_HIDE_CONTROLS_MS = 3500L
		private const val PROGRESS_REPORT_MS = 10_000L
	}

	private val api by inject<ApiClient>()
	private val playbackManager by inject<PlaybackManager>()
	private val videoQueueManager by inject<VideoQueueManager>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val reportingHelper by inject<ReportingHelper>()
	private val danmakuApi by inject<DanmakuApiRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val serverVersion by inject<ServerVersion>()
	private val interactionTracker by inject<InteractionTrackerViewModel>()

	private var player: ExoPlayer? = null
	private var danmakuView: DanmakuTextureView? = null
	private var danmakuManager: DanmakuManager? = null

	private var currentStreamInfo: StreamInfo? = null
	private var currentOptions: VideoOptions? = null
	private var currentItem: BaseItemDto? = null
	private var currentItemIndex = 0

	private var startPositionMs = 0L
	private var seekTargetMs = -1L

	private val handler = Handler(Looper.getMainLooper())
	private var progressReportRunnable: Runnable? = null
	private var hideControlsRunnable: Runnable? = null
	private var seekHintRunnable: Runnable? = null

	// UI state shared with Compose controls
	private var controlsVisible by mutableStateOf(false)
	private var isPlayingState by mutableStateOf(false)
	private var isBufferingState by mutableStateOf(true)
	private var positionState by mutableLongStateOf(0L)
	private var durationState by mutableLongStateOf(0L)
	private var bufferedState by mutableLongStateOf(0L)
	private var danmakuVisibleState by mutableStateOf(true)
	private var danmakuLoadedState by mutableStateOf(false)
	private var danmakuEntriesState by mutableStateOf<List<DanmakuListEntry>>(emptyList())
	private var danmakuListVisible by mutableStateOf(false)
	private var seekHintState by mutableStateOf<String?>(null)
	private var hasNextState by mutableStateOf(false)
	private var titleState by mutableStateOf("")
	private var playerReady by mutableStateOf(false)

	private var exiting = false
	private var screensaverLock: (() -> Unit)? = null

	// Danmaku display settings (preset indices, persisted in UserPreferences)
	private var danmakuSettingsVisible by mutableStateOf(false)
	private var danmakuTextSizeIdxState by mutableStateOf(userPreferences[UserPreferences.danmakuTextSizeIdx])
	private var danmakuSpeedIdxState by mutableStateOf(userPreferences[UserPreferences.danmakuSpeedIdx])
	private var danmakuOpacityIdxState by mutableStateOf(userPreferences[UserPreferences.danmakuOpacityIdx])
	private var danmakuAreaIdxState by mutableStateOf(userPreferences[UserPreferences.danmakuAreaIdx])

	private fun currentDanmakuSettings() = Triple(
		danmakuTextSizes[danmakuTextSizeIdxState.coerceIn(0, danmakuTextSizes.lastIndex)],
		danmakuSpeeds[danmakuSpeedIdxState.coerceIn(0, danmakuSpeeds.lastIndex)],
		danmakuOpacities[danmakuOpacityIdxState.coerceIn(0, danmakuOpacities.lastIndex)] / 100f
	) to danmakuAreas[danmakuAreaIdxState.coerceIn(0, danmakuAreas.lastIndex)]

	private fun applyDanmakuSettings() {
		val (sizeSpeed, area) = currentDanmakuSettings()
		danmakuManager?.applySettings(sizeSpeed.first, sizeSpeed.second, sizeSpeed.third, area)
	}

	/** cycling one preset forward for the given setting group (0 size, 1 speed, 2 area, 3 opacity) */
	private fun cycleDanmakuSetting(which: Int) {
		fun Int.cycled(size: Int) = (this + 1) % size
		when (which) {
			0 -> {
				danmakuTextSizeIdxState = danmakuTextSizeIdxState.cycled(danmakuTextSizes.size)
				userPreferences[UserPreferences.danmakuTextSizeIdx] = danmakuTextSizeIdxState
			}
			1 -> {
				danmakuSpeedIdxState = danmakuSpeedIdxState.cycled(danmakuSpeeds.size)
				userPreferences[UserPreferences.danmakuSpeedIdx] = danmakuSpeedIdxState
			}
			2 -> {
				danmakuAreaIdxState = danmakuAreaIdxState.cycled(danmakuAreas.size)
				userPreferences[UserPreferences.danmakuAreaIdx] = danmakuAreaIdxState
			}
			3 -> {
				danmakuOpacityIdxState = danmakuOpacityIdxState.cycled(danmakuOpacities.size)
				userPreferences[UserPreferences.danmakuOpacityIdx] = danmakuOpacityIdxState
			}
		}
		applyDanmakuSettings()
		// user is actively interacting: keep the controls visible
		scheduleHideControls()
	}

	// Playback failure fallback: direct play -> direct stream -> forced transcode
	private var playbackRetryCount = 0

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		BilibiliPlayerScreen()
	}

	@Composable
	private fun BilibiliPlayerScreen() {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black)
		) {
			val p = player
			if (playerReady && p != null) {
				AndroidView(
					modifier = Modifier.fillMaxSize(),
					factory = { ctx ->
						PlayerView(ctx).apply {
							useController = false
							player = p
						}
					},
					update = { it.player = p }
				)
			}
			AndroidView(
				modifier = Modifier.fillMaxSize(),
				factory = { ctx ->
					DanmakuTextureView(ctx).also {
						// SurfaceTexture creation happens in draw(); DFM sets willNotDraw(true)
						// which blocks it. Force it off so onSurfaceTextureAvailable fires.
						it.setWillNotDraw(false)
						danmakuView = it
					}
				}
			)
			BilibiliPlayerControls(
				visible = controlsVisible,
				title = titleState,
				isPlaying = isPlayingState,
				isBuffering = isBufferingState,
				positionMs = positionState,
				durationMs = durationState,
				bufferedMs = bufferedState,
				danmakuVisible = danmakuVisibleState,
				danmakuLoaded = danmakuLoadedState,
				danmakuEntries = danmakuEntriesState,
				danmakuListVisible = danmakuListVisible,
				hasNext = hasNextState,
				seekHint = seekHintState,
				danmakuSettingsExpanded = danmakuSettingsVisible,
				textSizeIdx = danmakuTextSizeIdxState,
				speedIdx = danmakuSpeedIdxState,
				opacityIdx = danmakuOpacityIdxState,
				areaIdx = danmakuAreaIdxState,
				onDanmakuSettingsExpandedChange = {
					danmakuSettingsVisible = it
					if (!it) scheduleHideControls() else hideControlsRunnable?.let { r -> handler.removeCallbacks(r) }
				},
				onDanmakuListVisibleChange = {
					danmakuListVisible = it
					if (it) {
						// Hide the bottom bar: the list becomes the only focusable
						// on screen so focus always lands inside it
						hideControls()
					} else {
						showControls()
					}
				},
				onCycleDanmakuSetting = ::cycleDanmakuSetting,
				onInteraction = ::scheduleHideControls,
				onDismiss = { hideControls() },
				onTogglePlay = ::togglePlayPause,
				onSeekTo = ::seekTo,
				onStepBack = { seekBy(-userSettingPreferences[UserSettingPreferences.skipBackLength].toLong()) },
				onStepForward = { seekBy(userSettingPreferences[UserSettingPreferences.skipForwardLength].toLong()) },
				onPlayNext = ::playNext,
				onToggleDanmaku = ::toggleDanmaku,
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setupPlayer()
		setupKeys(view)
		setupBackHandling()

		// Refresh position/buffered state periodically
		viewLifecycleOwner.lifecycleScope.launch {
			while (true) {
				delay(500)
				updatePositionState()
			}
		}

		startPositionMs = arguments?.getInt(EXTRA_POSITION, 0)?.toLong() ?: 0L
		loadItem(videoQueueManager.getCurrentMediaPosition(), startPositionMs)
	}

	private fun setupPlayer() {
		val context = requireContext()
		player = ExoPlayer.Builder(context)
			.setLoadControl(
				DefaultLoadControl.Builder()
					.setBufferDurationsMs(30_000, 60_000, 1_000, 3_000)
					.build()
			)
			.build()
			.also { exo ->
				exo.addListener(object : Player.Listener {
					override fun onIsPlayingChanged(isPlaying: Boolean) {
						isPlayingState = isPlaying
						if (isPlaying) {
							acquireScreensaverLock()
							scheduleHideControls()
						} else {
							releaseScreensaverLock()
						}
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						isBufferingState = playbackState == Player.STATE_BUFFERING
						if (playbackState == Player.STATE_READY) {
							seekTargetMs = -1
							playbackRetryCount = 0
							if (seekHintState?.startsWith("直连") == true || seekHintState?.startsWith("切换") == true) {
								seekHintState = null
							}
						}
						if (playbackState == Player.STATE_ENDED) {
							onItemEnded()
						}
						updatePositionState()
					}

					override fun onPlayerError(error: PlaybackException) {
						Timber.e(error, "BilibiliPlayer playback error (code=%s)", error.errorCodeName)
						onPlaybackError(error)
					}
				})
			}
		playerReady = true
	}

	private fun updatePositionState() {
		val p = player ?: return
		positionState = seekTargetMs.takeIf { it >= 0 } ?: p.currentPosition.coerceAtLeast(0)
		durationState = p.duration.takeIf { it > 0 } ?: 0
		bufferedState = p.bufferedPosition.coerceAtLeast(0)
	}

	private fun setupKeys(root: View) {
		root.isFocusable = true
		root.isFocusableInTouchMode = true
		root.requestFocus()
	}

	/**
	 * Key handling implemented as View.OnKeyListener so MainActivity's fragment key
	 * dispatch reaches us regardless of which child view currently holds focus
	 * (a root-view-only key listener missed keys until focus returned to the root).
	 */
	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event == null) return false
		val isDown = event.action == KeyEvent.ACTION_DOWN
		val isUp = event.action == KeyEvent.ACTION_UP
		// While the danmaku list panel is open, focus is trapped inside it (focusProperties)
		// and every key is handled by Compose; Back closes the panel
		if (controlsVisible || danmakuSettingsVisible || danmakuListVisible) return false
		return when (keyCode) {
			KeyEvent.KEYCODE_DPAD_LEFT,
			KeyEvent.KEYCODE_MEDIA_REWIND,
			KeyEvent.KEYCODE_BUTTON_L1 -> {
				if (isDown) seekBy(-userSettingPreferences[UserSettingPreferences.skipBackLength].toLong())
				true
			}
			KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
			KeyEvent.KEYCODE_BUTTON_R1 -> {
				if (isDown) seekBy(userSettingPreferences[UserSettingPreferences.skipForwardLength].toLong())
				true
			}
			KeyEvent.KEYCODE_DPAD_CENTER,
			KeyEvent.KEYCODE_ENTER,
			KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
				if (isUp) {
					togglePlayPause()
					// give visual feedback: show controls when pausing so the state is obvious
					if (player?.isPlaying == false) showControls()
				}
				true
			}
			KeyEvent.KEYCODE_DPAD_DOWN -> {
				if (isUp) showControls()
				true
			}
			KeyEvent.KEYCODE_MEDIA_PLAY -> {
				if (isUp) player?.play()
				true
			}
			KeyEvent.KEYCODE_MEDIA_PAUSE -> {
				if (isUp) player?.pause()
				true
			}
			else -> false
		}
	}

	private fun setupBackHandling() {
		requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
			when {
				danmakuListVisible -> danmakuListVisible = false
				danmakuSettingsVisible -> danmakuSettingsVisible = false
				controlsVisible -> hideControls()
				else -> exitPlayback()
			}
		}
	}

	// Playback flow

	private fun loadItem(index: Int, startMs: Long) {
		val queue = videoQueueManager.getCurrentVideoQueue()
		if (index !in queue.indices) {
			exitPlayback()
			return
		}

		videoQueueManager.setCurrentMediaPosition(index)
		currentItemIndex = index
		val item = queue[index]
		currentItem = item
		titleState = item.name ?: ""
		startPositionMs = startMs
		seekTargetMs = startMs
		positionState = startMs
		playbackRetryCount = 0
		danmakuLoadedState = false
		danmakuVisibleState = true
		danmakuEntriesState = emptyList()
		danmakuListVisible = false
		hasNextState = index < queue.size - 1
		reportStopInternal()

		resetDanmaku()

		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val options = buildOptions(item)
				currentOptions = options
				val stream = resolveStream(options, startMs * 10_000)
				currentStreamInfo = stream

				val p = player ?: return@launch
				p.setMediaItem(MediaItem.fromUri(stream.mediaUrl), startMs)
				p.prepare()
				p.play()

				loadDanmaku(item)
				reportStartInternal()
				startProgressReportLoop()
				showControls()
			} catch (e: Exception) {
				Timber.e(e, "Failed to start playback for %s", item.name)
				Toast.makeText(requireContext(), R.string.msg_video_playback_error, Toast.LENGTH_LONG).show()
				exitPlayback()
			}
		}
	}

	private suspend fun buildOptions(item: BaseItemDto): VideoOptions = VideoOptions().apply {
		itemId = item.id
		profile = createDeviceProfile(requireContext(), userPreferences, serverVersion)
		enableDirectPlay = true
		enableDirectStream = true
		alwaysBurnInSubtitleWhenTranscoding =
			userPreferences[UserPreferences.subtitlesBurnDuringTranscode]
	}

	private suspend fun resolveStream(options: VideoOptions, startTicks: Long): StreamInfo =
		suspendCoroutine { continuation ->
			playbackManager.getVideoStreamInfo(
				viewLifecycleOwner,
				options,
				startTicks,
				object : org.jellyfin.androidtv.util.apiclient.Response<StreamInfo>(viewLifecycleOwner.lifecycle) {
					override fun onResponse(response: StreamInfo) = continuation.resume(response)
					override fun onError(exception: Exception) = continuation.resumeWithException(exception)
				}
			)
		}

	// Seek: never pauses playback; rebuilds the stream when the current one is not seekable

	private fun seekBy(deltaMs: Long) {
		val p = player ?: return
		val base = if (seekTargetMs >= 0) seekTargetMs else p.currentPosition
		seekTo(base + deltaMs)
	}

	private fun seekTo(positionMs: Long) {
		val p = player ?: return
		val duration = p.duration
		val current = if (seekTargetMs >= 0) seekTargetMs else p.currentPosition
		val target = if (duration > 0) positionMs.coerceIn(0, duration - 500) else positionMs.coerceAtLeast(0)

		showSeekHint(if (target >= current) "+${formatTime(target)}" else "-${formatTime(target)}")

		if (p.isCurrentMediaItemSeekable) {
			seekTargetMs = target
			p.seekTo(target)
			if (!p.isPlaying) p.play()
		} else {
			// Transcoded stream without seeking support: rebuild the stream at the new position
			rebuildStreamAt(target)
		}
	}

	private fun rebuildStreamAt(positionMs: Long) {
		val options = currentOptions ?: return
		player?.stop()
		seekTargetMs = positionMs
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val stream = resolveStream(options, positionMs * 10_000)
				currentStreamInfo = stream
				val p = player ?: return@launch
				p.setMediaItem(MediaItem.fromUri(stream.mediaUrl), positionMs)
				p.prepare()
				p.play()
			} catch (e: Exception) {
				Timber.e(e, "Failed to rebuild stream for seeking")
			}
		}
	}

	private fun showSeekHint(text: String) {
		seekHintState = text
		seekHintRunnable?.let { handler.removeCallbacks(it) }
		val runnable = Runnable { seekHintState = null }
		seekHintRunnable = runnable
		handler.postDelayed(runnable, 1000)
	}

	// Controls visibility

	/**
	 * Keeps the screen awake and prevents the in-app screensaver while playing.
	 * Same mechanism the legacy player uses (InteractionTrackerViewModel locks).
	 */
	private fun acquireScreensaverLock() {
		if (screensaverLock != null) return
		screensaverLock = interactionTracker.addLifecycleLock(viewLifecycleOwner.lifecycle)
	}

	private fun releaseScreensaverLock() {
		screensaverLock?.invoke()
		screensaverLock = null
	}

	private fun showControls() {
		controlsVisible = true
		updatePositionState()
		scheduleHideControls()
	}

	private fun hideControls() {
		controlsVisible = false
		hideControlsRunnable?.let { handler.removeCallbacks(it) }
	}

	private fun scheduleHideControls() {
		// Never auto-hide while the danmaku settings popup or list panel is open:
		// just drop the pending callback entirely, it is re-scheduled on close
		if (danmakuSettingsVisible || danmakuListVisible) {
			hideControlsRunnable?.let { handler.removeCallbacks(it) }
			return
		}
		if (hideControlsRunnable == null) {
			hideControlsRunnable = Runnable {
				if (danmakuSettingsVisible || danmakuListVisible) return@Runnable
				controlsVisible = false
			}
		}
		handler.removeCallbacks(hideControlsRunnable!!)
		handler.postDelayed(hideControlsRunnable!!, AUTO_HIDE_CONTROLS_MS)
	}

	private fun togglePlayPause() {
		val p = player ?: return
		if (p.isPlaying) p.pause() else p.play()
		scheduleHideControls()
	}

	// Queue navigation

	/**
	 * Automatic playback failure fallback:
	 * 1st failure: force server-side transcode (real-time encode).
	 * We intentionally skip the copy-remux (direct stream) level: for MKVs with a
	 * broken/missing Cues index the server remuxes the whole file in seconds, then
	 * deletes the HLS playlist before a slow TV client ever fetches it.
	 * Real-time transcoding keeps the job (and the playlist) alive.
	 */
	private fun onPlaybackError(error: PlaybackException) {
		val item = currentItem ?: return
		if (exiting) return

		val positionMs = player?.currentPosition?.coerceAtLeast(0) ?: 0
		if (playbackRetryCount == 0) {
			playbackRetryCount = 1
			seekHintState = "直连播放失败，切换服务器转码…"
			rebuildStream(item, positionMs, directPlay = false, directStream = false)
		} else {
			seekHintState = "无法播放该视频（错误: ${error.errorCodeName}）"
			handler.postDelayed({ if (seekHintState?.startsWith("无法播放") == true) seekHintState = null }, 4000)
			Toast.makeText(requireContext(), R.string.msg_video_playback_error, Toast.LENGTH_LONG).show()
		}
	}

	private fun rebuildStream(item: BaseItemDto, positionMs: Long, directPlay: Boolean, directStream: Boolean) {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val options = buildOptions(item).apply {
					enableDirectPlay = directPlay
					enableDirectStream = directStream
				}
				currentOptions = options
				val stream = resolveStream(options, positionMs * 10_000)
				currentStreamInfo = stream

				val p = player ?: return@launch
				p.setMediaItem(MediaItem.fromUri(stream.mediaUrl), positionMs)
				p.prepare()
				p.play()
				Timber.i("Playback retry with directPlay=%s directStream=%s playMethod=%s", directPlay, directStream, stream.playMethod)
			} catch (e: Exception) {
				Timber.e(e, "Playback retry failed")
				seekHintState = "播放失败：${e.message}"
				Toast.makeText(requireContext(), R.string.msg_video_playback_error, Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun playNext() {
		if (currentItemIndex < videoQueueManager.getCurrentVideoQueue().size - 1) {
			loadItem(currentItemIndex + 1, 0)
		}
	}

	private fun onItemEnded() {
		if (exiting) return
		val item = currentItem ?: return
		val stream = currentStreamInfo
		if (item != null && stream != null) {
			reportingHelper.reportStopped(viewLifecycleOwner, item, stream, item.runTimeTicks)
		}
		if (hasNextState) {
			playNext()
		} else {
			exitPlayback()
		}
	}

	private fun exitPlayback() {
		if (exiting) return
		exiting = true
		reportStopInternal()
		stopProgressReportLoop()
		danmakuManager?.release()
		danmakuManager = null
		player?.release()
		player = null
		navigationRepository.goBack()
	}

	// Playback reporting

	private fun reportStartInternal() {
		val item = currentItem ?: return
		val stream = currentStreamInfo ?: return
		// ReportingHelper expects ticks (1 ms = 10_000 ticks), same as the legacy player
		reportingHelper.reportStart(
			viewLifecycleOwner, null, item, stream, (player?.currentPosition ?: startPositionMs) * 10_000, false
		)
	}

	private fun reportProgressInternal() {
		if (exiting) return
		val item = currentItem ?: return
		val stream = currentStreamInfo ?: return
		reportingHelper.reportProgress(
			viewLifecycleOwner, null, item, stream, (player?.currentPosition ?: 0) * 10_000, player?.isPlaying == false
		)
	}

	private fun reportStopInternal() {
		val item = currentItem ?: return
		val stream = currentStreamInfo ?: return
		reportingHelper.reportStopped(viewLifecycleOwner, item, stream, player?.currentPosition?.times(10_000))
	}

	private fun startProgressReportLoop() {
		stopProgressReportLoop()
		val runnable = object : Runnable {
			override fun run() {
				reportProgressInternal()
				handler.postDelayed(this, PROGRESS_REPORT_MS)
			}
		}
		progressReportRunnable = runnable
		handler.postDelayed(runnable, PROGRESS_REPORT_MS)
	}

	private fun stopProgressReportLoop() {
		progressReportRunnable?.let { handler.removeCallbacks(it) }
		progressReportRunnable = null
	}

	// Danmaku

	private fun resetDanmaku() {
		danmakuManager?.release()
		danmakuManager = null
	}

	private fun loadDanmaku(item: BaseItemDto) {
		viewLifecycleOwner.lifecycleScope.launch {
			// The danmaku view is created inside a Compose AndroidView factory;
			// wait until it exists before handing it to the danmaku engine
			val view: DanmakuTextureView = withTimeoutOrNull(5000) {
				while (danmakuView == null) delay(50)
				danmakuView
			} ?: run {
				Timber.w("Danmaku view not available in time")
				return@launch
			}

			var xml = runCatching { danmakuApi.getDanmakuRaw(item.id.toString()) }.getOrNull()

			if (xml.isNullOrBlank()) {
				// No cached danmaku on the server: request an auto-match and poll for it
				Timber.d("No cached danmaku for %s, requesting refresh", item.name)
				runCatching { danmakuApi.refreshDanmaku(item.id.toString(), force = true) }
				repeat(10) {
					delay(3000)
					val info = runCatching { danmakuApi.getDanmakuInfo(item.id.toString()) }.getOrNull()
					if (info?.hasDanmaku == true) {
						xml = runCatching { danmakuApi.getDanmakuRaw(item.id.toString()) }.getOrNull()
					}
					if (!xml.isNullOrBlank()) return@repeat
				}
			}

			if (xml.isNullOrBlank()) {
				Timber.d("No danmaku data for %s", item.name)
				return@launch
			}

			try {
				// Parse the raw XML for the danmaku list side panel (safe metadata
				// parse, independent of the danmaku engine's own parse)
				danmakuEntriesState = parseDanmakuList(xml)

				val manager = DanmakuManager(requireActivity())
				manager.initialize(view)
				// Bypass CacheManagingDrawTask (its build-cache thread previously crashed
				// with NPE and may silently produce empty caches); plain DrawTask renders
				// text directly and is much simpler.
				view.enableDanmakuDrawingCache(false)
				val parser = JellyfinDanmakuParser()
				manager.onEnginePrepared = {
					// Called on the main thread (posted) after the engine thread finished
					// parsing - safe to query the danmaku set here.
					val count = runCatching { parser.getDanmakus().size() }.getOrDefault(-1)
					Timber.d("Danmaku engine prepared, %s danmakus loaded", count)
				}
				val dataSource = DanmakuDataSource()
				dataSource.loadFromString(xml)
				parser.load(dataSource)
				// The manager defers start() until the engine reports prepared().
				// NOTE: never call parser.getDanmakus() from the main thread - it races
				// with the engine's own parse (shared input stream + releaseDataSource)
				// and can leave the engine with an empty danmaku set.
				manager.loadDanmaku(parser, player?.currentPosition ?: 0)
				danmakuManager = manager
				danmakuLoadedState = true
				applyDanmakuSettings()
				Timber.d("Danmaku load requested for %s", item.name)
			} catch (e: Exception) {
				Timber.e(e, "Failed to load danmaku")
			}
		}
	}

	private fun toggleDanmaku() {
		if (!danmakuLoadedState) {
			Toast.makeText(requireContext(), R.string.msg_danmaku_not_loaded, Toast.LENGTH_SHORT).show()
			return
		}
		danmakuVisibleState = !danmakuVisibleState
		// setVisible toggles show/hide on the engine; a follow-up start() would clear the
		// pending SHOW message (removeCallbacksAndMessages) and danmaku would stay hidden
		danmakuManager?.setVisible(danmakuVisibleState)
		scheduleHideControls()
	}

	// Lifecycle

	override fun onResume() {
		super.onResume()
		if (!exiting) player?.play()
	}

	override fun onPause() {
		super.onPause()
		player?.pause()
	}

	override fun onStop() {
		super.onStop()
		reportProgressInternal()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		exiting = true
		releaseScreensaverLock()
		stopProgressReportLoop()
		handler.removeCallbacksAndMessages(null)
		danmakuManager?.release()
		danmakuManager = null
		player?.release()
		player = null
	}
}
