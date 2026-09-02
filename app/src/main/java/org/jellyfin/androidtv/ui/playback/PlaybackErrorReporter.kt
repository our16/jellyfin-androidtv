package org.jellyfin.androidtv.ui.playback

import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comprehensive playback error reporter for debugging playback failures.
 * Logs detailed information about errors, stream configuration, and device state.
 */
object PlaybackErrorReporter {

    private const val TAG = "PlaybackError"

    /**
     * Log a detailed playback error with full context.
     */
    fun reportError(
        error: Throwable,
        streamInfo: StreamInfo?,
        retryCount: Int = 0,
        currentPosition: Long = 0,
    ) {
        val sb = StringBuilder()

        sb.appendLine("=== PLAYBACK ERROR REPORT ===")
        sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
        sb.appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine()

        // Error details
        sb.appendLine("--- ERROR ---")
        sb.appendLine("Type: ${error.javaClass.simpleName}")
        sb.appendLine("Message: ${error.message}")
        sb.appendLine("Retry Count: $retryCount")
        sb.appendLine("Current Position: ${currentPosition}ms")
        sb.appendLine()

        // Stack trace (first 10 lines)
        sb.appendLine("--- STACK TRACE (first 10 lines) ---")
        error.stackTrace.take(10).forEach { element ->
            sb.appendLine("  at $element")
        }
        if (error.stackTrace.size > 10) {
            sb.appendLine("  ... (${error.stackTrace.size - 10} more lines)")
        }
        sb.appendLine()

        // Stream info
        if (streamInfo != null) {
            sb.appendLine("--- STREAM INFO ---")
            sb.appendLine("Item ID: ${streamInfo.itemId}")
            sb.appendLine("Play Method: ${streamInfo.playMethod}")
            sb.appendLine("Container: ${streamInfo.container}")
            sb.appendLine("Play Session ID: ${streamInfo.playSessionId}")
            sb.appendLine("Media URL: ${streamInfo.mediaUrl?.take(200)}")
            sb.appendLine()

            // Media source details
            val mediaSource = streamInfo.mediaSource
            if (mediaSource != null) {
                sb.appendLine("--- MEDIA SOURCE ---")
                sb.appendLine("Source ID: ${mediaSource.id}")
                sb.appendLine("Name: ${mediaSource.name}")
                sb.appendLine("Container: ${mediaSource.container}")
                sb.appendLine("Run Time: ${mediaSource.runTimeTicks?.div(10000)}ms")
                sb.appendLine("Supports Direct Play: ${mediaSource.supportsDirectPlay}")
                sb.appendLine("Supports Direct Stream: ${mediaSource.supportsDirectStream}")
                sb.appendLine("Supports Transcoding: ${mediaSource.supportsTranscoding}")
                sb.appendLine("Transcoding URL: ${mediaSource.transcodingUrl?.take(200)}")
                sb.appendLine("Path: ${mediaSource.path?.take(200)}")
                sb.appendLine()

                // Media streams
                sb.appendLine("--- MEDIA STREAMS ---")
                mediaSource.mediaStreams?.forEach { stream ->
                    sb.appendLine("  [${stream.index}] ${stream.type}: ${stream.codec ?: "unknown"}")
                    sb.appendLine("    Language: ${stream.language ?: "unknown"}")
                    sb.appendLine("    Title: ${stream.title ?: "unknown"}")
                    sb.appendLine("    Default: ${stream.isDefault}, Forced: ${stream.isForced}")
                    sb.appendLine("    External: ${stream.isExternal}")
                    sb.appendLine("    Delivery Method: ${stream.deliveryMethod}")
                    when (stream.type) {
                        MediaStreamType.VIDEO -> {
                            sb.appendLine("    Width: ${stream.width}, Height: ${stream.height}")
                            sb.appendLine("    Bit Rate: ${stream.bitRate?.div(1000)}kbps")
                            sb.appendLine("    Frame Rate: ${stream.realFrameRate}")
                            sb.appendLine("    Profile: ${stream.profile}")
                            sb.appendLine("    Level: ${stream.level}")
                            sb.appendLine("    Color Space: ${stream.colorSpace}")
                            sb.appendLine("    Color Range: ${stream.colorRange}")
                            sb.appendLine("    Video Range Type: ${stream.videoRangeType}")
                        }
                        MediaStreamType.AUDIO -> {
                            sb.appendLine("    Channels: ${stream.channels}")
                            sb.appendLine("    Sample Rate: ${stream.sampleRate}")
                            sb.appendLine("    Bit Depth: ${stream.bitDepth}")
                            sb.appendLine("    Bit Rate: ${stream.bitRate?.div(1000)}kbps")
                            sb.appendLine("    Channel Layout: ${stream.channelLayout}")
                        }
                        else -> {}
                    }
                }
            }
        }

        sb.appendLine("=== END REPORT ===")

        // Log to Timber (will appear in logcat)
        Timber.e(sb.toString())

        // Also log to stdout for immediate visibility
        println(sb.toString())
    }

    /**
     * Log stream configuration when playback starts.
     */
    fun logStreamConfig(streamInfo: StreamInfo) {
        val sb = StringBuilder()

        sb.appendLine("=== PLAYBACK START ===")
        sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
        sb.appendLine("Play Method: ${streamInfo.playMethod}")
        sb.appendLine("Container: ${streamInfo.container}")
        sb.appendLine("Media URL: ${streamInfo.mediaUrl?.take(200)}")

        val mediaSource = streamInfo.mediaSource
        if (mediaSource != null) {
            // Find video stream
            val videoStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
            if (videoStream != null) {
                sb.appendLine("Video: ${videoStream.codec} ${videoStream.width}x${videoStream.height} ${videoStream.bitRate?.div(1000)}kbps")
                sb.appendLine("  Profile: ${videoStream.profile}, Level: ${videoStream.level}")
                sb.appendLine("  Video Range: ${videoStream.videoRangeType}")
            }

            // Find audio stream
            val audioStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
            if (audioStream != null) {
                sb.appendLine("Audio: ${audioStream.codec} ${audioStream.channels}ch ${audioStream.sampleRate}Hz ${audioStream.bitRate?.div(1000)}kbps")
            }

            // Subtitle
            val subtitleStream = mediaSource.mediaStreams?.firstOrNull {
                it.type == MediaStreamType.SUBTITLE && it.isDefault == true
            }
            if (subtitleStream != null) {
                sb.appendLine("Subtitle: ${subtitleStream.codec} ${subtitleStream.language} (${subtitleStream.deliveryMethod})")
            }
        }

        sb.appendLine("=== END START ===")

        Timber.i(sb.toString())
    }

    /**
     * Log device and configuration info.
     */
    fun logDeviceConfig() {
        val sb = StringBuilder()

        sb.appendLine("=== DEVICE CONFIG ===")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("Hardware: ${android.os.Build.HARDWARE}")
        sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Cores: ${Runtime.getRuntime().availableProcessors()}")
        sb.appendLine("Max Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
        sb.appendLine("=== END DEVICE CONFIG ===")

        Timber.i(sb.toString())
    }
}
