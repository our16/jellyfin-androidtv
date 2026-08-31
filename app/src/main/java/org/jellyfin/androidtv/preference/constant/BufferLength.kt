package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class BufferLength(
	override val nameRes: Int,
	val minBufferDuration: Duration? = null,
	val maxBufferDuration: Duration? = null,
	val bufferForPlaybackDuration: Duration? = null,
	val bufferForPlaybackAfterRebufferDuration: Duration? = null,
) : PreferenceEnum {
	/**
	 * Use default buffer durations.
	 */
	AUTO(R.string.playback_buffer_auto),

	/**
	 * Larger buffer, suitable for moderate or variable connections.
	 */
	@Suppress("MagicNumber")
	LARGE(
		nameRes = R.string.playback_buffer_large,
		minBufferDuration = 50.seconds,
		maxBufferDuration = 120.seconds,
		bufferForPlaybackDuration = 2500.milliseconds,
		bufferForPlaybackAfterRebufferDuration = 5.seconds,
	),

	/**
	 * Maximum buffer, intended for slow or satellite connections.
	 */
	@Suppress("MagicNumber")
	EXTRA_LARGE(
		nameRes = R.string.playback_buffer_extra_large,
		minBufferDuration = 80.seconds,
		maxBufferDuration = 240.seconds,
		bufferForPlaybackDuration = 5.seconds,
		bufferForPlaybackAfterRebufferDuration = 10.seconds,
	),

	/**
	 * Large buffer optimized for 4K content on devices with sufficient RAM (4GB+).
	 * Prevents stuttering during high-bitrate 4K HEVC playback.
	 */
	@Suppress("MagicNumber")
	LARGE_4K(
		nameRes = R.string.playback_buffer_large_4k,
		minBufferDuration = 60.seconds,
		maxBufferDuration = 180.seconds,
		bufferForPlaybackDuration = 3.seconds,
		bufferForPlaybackAfterRebufferDuration = 6.seconds,
	),
}
