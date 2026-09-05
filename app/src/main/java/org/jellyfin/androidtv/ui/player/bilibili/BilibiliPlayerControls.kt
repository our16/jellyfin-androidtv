package org.jellyfin.androidtv.ui.player.bilibili

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

val BiliPink = Color(0xFFFB7299)

fun formatTime(ms: Long): String {
	val totalSeconds = ms / 1000
	val h = totalSeconds / 3600
	val m = (totalSeconds % 3600) / 60
	val s = totalSeconds % 60
	return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
fun BilibiliPlayerControls(
	visible: Boolean,
	title: String,
	isPlaying: Boolean,
	isBuffering: Boolean,
	positionMs: Long,
	durationMs: Long,
	bufferedMs: Long,
	danmakuVisible: Boolean,
	danmakuLoaded: Boolean,
	hasNext: Boolean,
	seekHint: String?,
	onDismiss: () -> Unit,
	onTogglePlay: () -> Unit,
	onSeekTo: (Long) -> Unit,
	onStepBack: () -> Unit,
	onStepForward: () -> Unit,
	onPlayNext: () -> Unit,
	onToggleDanmaku: () -> Unit,
) {
	Box(modifier = Modifier.fillMaxSize()) {
		if (visible && isBuffering) {
			Box(
				modifier = Modifier
					.align(Alignment.Center)
					.size(56.dp)
					.border(4.dp, Color.White, CircleShape)
			)
		}
		if (seekHint != null) {
			Text(
				text = seekHint,
				color = Color.White,
				fontSize = 22.sp,
				modifier = Modifier
					.align(Alignment.Center)
					.background(Color(0x99000000), RoundedCornerShape(8.dp))
					.padding(horizontal = 16.dp, vertical = 8.dp)
			)
		}

		AnimatedVisibility(
			visible = visible,
			enter = fadeIn() + slideInVertically { -it },
			exit = fadeOut() + slideOutVertically { -it },
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
					.padding(horizontal = 28.dp, vertical = 14.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(text = title, color = Color.White, fontSize = 20.sp, maxLines = 1)
			}
		}

		AnimatedVisibility(
			visible = visible,
			enter = fadeIn() + slideInVertically { it },
			exit = fadeOut() + slideOutVertically { it },
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			BottomBar(
				isPlaying = isPlaying,
				positionMs = positionMs,
				durationMs = durationMs,
				bufferedMs = bufferedMs,
				danmakuVisible = danmakuVisible,
				danmakuLoaded = danmakuLoaded,
				hasNext = hasNext,
				onTogglePlay = onTogglePlay,
				onSeekTo = onSeekTo,
				onStepBack = onStepBack,
				onStepForward = onStepForward,
				onPlayNext = onPlayNext,
				onToggleDanmaku = onToggleDanmaku,
			)
		}
	}
}

@Composable
private fun BottomBar(
	isPlaying: Boolean,
	positionMs: Long,
	durationMs: Long,
	bufferedMs: Long,
	danmakuVisible: Boolean,
	danmakuLoaded: Boolean,
	hasNext: Boolean,
	onTogglePlay: () -> Unit,
	onSeekTo: (Long) -> Unit,
	onStepBack: () -> Unit,
	onStepForward: () -> Unit,
	onPlayNext: () -> Unit,
	onToggleDanmaku: () -> Unit,
) {
	val focusRequester = remember { FocusRequester() }
	var previewMs by remember { mutableLongStateOf(-1L) }

	LaunchedEffect(previewMs) {
		if (previewMs >= 0) {
			delay(700)
			onSeekTo(previewMs)
			previewMs = -1
		}
	}

	LaunchedEffect(Unit) {
		focusRequester.requestFocus()
	}

	val displayPos = if (previewMs >= 0) previewMs else positionMs

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
			.padding(horizontal = 32.dp, vertical = 8.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(32.dp)
				.focusRequester(focusRequester)
				.focusable()
				.onKeyEvent { event ->
					when (event.key) {
						Key.DirectionLeft -> {
							if (durationMs > 0) {
								previewMs = ((if (previewMs < 0) positionMs else previewMs) - 10_000L)
									.coerceIn(0L, durationMs)
							}
							true
						}
						Key.DirectionRight -> {
							if (durationMs > 0) {
								previewMs = ((if (previewMs < 0) positionMs else previewMs) + 10_000L)
									.coerceIn(0L, durationMs)
							}
							true
						}
						Key.DirectionCenter, Key.Enter -> {
							if (previewMs >= 0) {
								onSeekTo(previewMs)
								previewMs = -1
							}
							true
						}
						else -> false
					}
				},
		) {
			BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
				val fraction = if (durationMs > 0) {
					displayPos.toFloat().coerceIn(0f, durationMs.toFloat()) / durationMs
				} else 0f
				val bufferedFraction = if (durationMs > 0) {
					bufferedMs.toFloat().coerceIn(0f, durationMs.toFloat()) / durationMs
				} else 0f

				Box(
					modifier = Modifier
						.align(Alignment.CenterStart)
						.fillMaxWidth()
						.height(6.dp)
						.background(Color(0x40FFFFFF), RoundedCornerShape(3.dp))
				)
				Box(
					modifier = Modifier
						.align(Alignment.CenterStart)
						.fillMaxWidth(bufferedFraction)
						.height(6.dp)
						.background(Color(0x66FFFFFF), RoundedCornerShape(3.dp))
				)
				Box(
					modifier = Modifier
						.align(Alignment.CenterStart)
						.fillMaxWidth(fraction)
						.height(6.dp)
						.background(BiliPink, RoundedCornerShape(3.dp))
				)
				Box(
					modifier = Modifier
						.align(Alignment.CenterStart)
						.absoluteOffset(x = maxWidth * fraction - 3.dp)
						.width(6.dp)
						.height(22.dp)
						.background(Color.White, RoundedCornerShape(3.dp))
				)
			}
		}

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ControlButton(
				icon = painterResource(if (isPlaying) R.drawable.ic_bili_pause else R.drawable.ic_bili_play),
				contentDescription = "play/pause",
				onClick = onTogglePlay,
			)
			ControlButton(
				icon = painterResource(R.drawable.ic_bili_replay10),
				contentDescription = "back",
				onClick = onStepBack,
			)
			ControlButton(
				icon = painterResource(R.drawable.ic_bili_forward10),
				contentDescription = "forward",
				onClick = onStepForward,
			)
			Text(
				text = "${formatTime(displayPos)} / ${formatTime(durationMs)}",
				color = Color.White,
				fontSize = 15.sp,
				modifier = Modifier.padding(start = 12.dp),
			)

			Spacer(modifier = Modifier.weight(1f))

			ControlButton(
				icon = painterResource(if (danmakuVisible) R.drawable.ic_danmaku_on else R.drawable.ic_danmaku_off),
				contentDescription = "danmaku",
				tint = if (danmakuLoaded && danmakuVisible) BiliPink else Color.White,
				onClick = onToggleDanmaku,
			)
			if (hasNext) {
				ControlButton(
					icon = painterResource(R.drawable.ic_bili_next),
					contentDescription = "next",
					onClick = onPlayNext,
				)
			}
		}
	}
}

@Composable
private fun ControlButton(
	icon: Painter,
	contentDescription: String,
	tint: Color = Color.White,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	Box(
		modifier = Modifier
			.padding(4.dp)
			.size(44.dp)
			.background(
				if (focused) Color(0x33FFFFFF) else Color.Transparent,
				RoundedCornerShape(10.dp)
			)
			.then(
				if (focused) Modifier.border(2.dp, BiliPink, RoundedCornerShape(10.dp)) else Modifier
			)
			.onFocusChanged { focused = it.isFocused }
			.focusable()
			.onKeyEvent { event ->
				if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
					onClick()
					true
				} else false
			},
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = icon,
			contentDescription = contentDescription,
			colorFilter = ColorFilter.tint(tint),
			contentScale = ContentScale.Fit,
			modifier = Modifier.size(26.dp),
		)
	}
}
