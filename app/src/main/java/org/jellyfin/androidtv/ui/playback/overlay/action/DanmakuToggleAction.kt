package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class DanmakuToggleAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	companion object {
		const val INDEX_OFF = 0
		const val INDEX_ON = 1
	}

	init {
		val offIcon = ContextCompat.getDrawable(context, R.drawable.ic_danmaku_off)
		val onIcon = ContextCompat.getDrawable(context, R.drawable.ic_danmaku_on)
		setDrawables(arrayOf(offIcon, onIcon))
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		playbackController.toggleDanmaku()
		this.index = if (playbackController.isDanmakuVisible) INDEX_ON else INDEX_OFF
	}
}
