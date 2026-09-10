package org.jellyfin.androidtv.ui.player.bilibili

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.modifier.autoFocus

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
	danmakuEntries: List<DanmakuListEntry>,
	danmakuListVisible: Boolean,
	danmakuListSelectedIndex: Int,
	hasNext: Boolean,
	seekHint: String?,
	danmakuSettingsExpanded: Boolean,
	textSizeIdx: Int,
	speedIdx: Int,
	opacityIdx: Int,
	areaIdx: Int,
	onDanmakuSettingsExpandedChange: (Boolean) -> Unit,
	onDanmakuListVisibleChange: (Boolean) -> Unit,
	onCycleDanmakuSetting: (Int) -> Unit,
	onInteraction: () -> Unit,
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

		// Danmaku list side panel (right side of the screen)
		if (danmakuListVisible) {
			DanmakuListPanel(
				entries = danmakuEntries,
				selectedIndex = danmakuListSelectedIndex,
				modifier = Modifier
					.align(Alignment.CenterEnd)
					.padding(end = 24.dp),
			)		}

		// Central play button while paused: gives focus a clear target so the
		// progress bar does not swallow OK presses while paused. Shown even when
		// the other controls auto-hid so focus stays anchored on it.
		if (!isPlaying && !isBuffering && !danmakuSettingsExpanded && !danmakuListVisible) {
			CentralPlayButton(
				onClick = onTogglePlay,
				modifier = Modifier.align(Alignment.Center),
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
				hasNext = hasNext,
				danmakuSettingsExpanded = danmakuSettingsExpanded,
				danmakuListVisible = danmakuListVisible,
				textSizeIdx = textSizeIdx,
				speedIdx = speedIdx,
				opacityIdx = opacityIdx,
				areaIdx = areaIdx,
				onDanmakuSettingsExpandedChange = onDanmakuSettingsExpandedChange,
				onCycleDanmakuSetting = onCycleDanmakuSetting,
				onInteraction = onInteraction,
				onTogglePlay = onTogglePlay,
				onSeekTo = onSeekTo,
				onStepBack = onStepBack,
				onStepForward = onStepForward,
				onPlayNext = onPlayNext,
			)
		}

		// Settings popup anchored at the bottom-right, right above the gear button.
		// Lives on the root overlay (NOT inside the bottom bar) so it can never
		// inflate the bar's layout and push the progress bar around.
		if (danmakuSettingsExpanded) {
			DanmakuSettingsPopup(
				danmakuVisible = danmakuVisible,
				danmakuLoaded = danmakuLoaded,
				textSizeIdx = textSizeIdx,
				speedIdx = speedIdx,
				opacityIdx = opacityIdx,
				areaIdx = areaIdx,
				onCycle = onCycleDanmakuSetting,
				onToggleDanmaku = onToggleDanmaku,
				onOpenList = {
					onDanmakuSettingsExpandedChange(false)
					onDanmakuListVisibleChange(true)
				},
				onInteraction = onInteraction,
				onDismiss = { onDanmakuSettingsExpandedChange(false) },
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.offset(x = (-16).dp, y = (-112).dp),
			)
		}
	}
}

// Danmaku display settings presets
val danmakuTextSizes = floatArrayOf(0.8f, 1.0f, 1.3f, 1.6f)
val danmakuTextSizeLabels = arrayOf("小", "标准", "大", "特大")
val danmakuSpeeds = floatArrayOf(1.5f, 1.2f, 0.9f, 0.6f)
val danmakuSpeedLabels = arrayOf("慢", "正常", "快", "极快")
val danmakuOpacities = intArrayOf(30, 50, 70, 85, 100)
val danmakuAreas = floatArrayOf(0.35f, 0.5f, 0.75f, 1.0f)
val danmakuAreaLabels = arrayOf("1/3屏", "半屏", "3/4屏", "全屏")

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun BottomBar(
	isPlaying: Boolean,
	positionMs: Long,
	durationMs: Long,
	bufferedMs: Long,
	hasNext: Boolean,
	danmakuSettingsExpanded: Boolean,
	danmakuListVisible: Boolean,
	textSizeIdx: Int,
	speedIdx: Int,
	opacityIdx: Int,
	areaIdx: Int,
	onDanmakuSettingsExpandedChange: (Boolean) -> Unit,
	onCycleDanmakuSetting: (Int) -> Unit,
	onInteraction: () -> Unit,
	onTogglePlay: () -> Unit,
	onSeekTo: (Long) -> Unit,
	onStepBack: () -> Unit,
	onStepForward: () -> Unit,
	onPlayNext: () -> Unit,
) {
	val focusRequester = remember { FocusRequester() }
	val scope = rememberCoroutineScope()

	// Scrub preview: LEFT/RIGHT move a preview marker immediately (with the
	// progressive ladder), but the actual seek fires only after a short pause -
	// this debounces transcode restarts while the user is still pressing
	var previewMs by remember { mutableLongStateOf(-1L) }
	var scrubComboDir by remember { mutableIntStateOf(0) }
	var scrubComboCount by remember { mutableIntStateOf(0) }
	var scrubComboLastAt by remember { mutableLongStateOf(0L) }
	var scrubJob by remember { mutableStateOf< kotlinx.coroutines.Job?>(null) }

	fun scrubStep(direction: Int): Long {
		val now = android.os.SystemClock.uptimeMillis()
		if (direction != scrubComboDir || now - scrubComboLastAt > 2500) {
			scrubComboDir = direction
			scrubComboCount = 0
		}
		scrubComboLastAt = now
		val ladder = longArrayOf(10, 30, 60, 120, 300)
		val step = ladder[scrubComboCount.coerceIn(0, ladder.lastIndex)]
		scrubComboCount++
		return step * 1000
	}

	fun scrubTo(target: Long) {
		previewMs = target
		scrubJob?.cancel()
		scrubJob = scope.launch {
			delay(550)
			onSeekTo(previewMs)
			previewMs = -1
		}
	}

	// Focus watchdog: while PLAYING, if focus ever falls outside the bottom bar
	// (repeated DOWN can push it into no-focus limbo where keys stop working),
	// pull it back to the progress bar. While paused the central play button is
	// the anchor and is left alone.
	var barHasFocus by remember { mutableStateOf(false) }
	LaunchedEffect(barHasFocus, isPlaying, danmakuSettingsExpanded, danmakuListVisible) {
		while (isPlaying && !barHasFocus && !danmakuSettingsExpanded && !danmakuListVisible) {
			timber.log.Timber.d("BarFocus: watchdog requesting focus")
			runCatching { focusRequester.requestFocus() }
			delay(150)
		}
		timber.log.Timber.d("BarFocus: watchdog satisfied (bar has focus)")
	}

	val displayPos = if (previewMs >= 0) previewMs else positionMs
	var barFocused by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
			.padding(horizontal = 32.dp, vertical = 8.dp)
			.onFocusChanged { barHasFocus = it.hasFocus }
			.focusProperties {
				// While the settings popup or danmaku list is open the bottom bar
				// must not receive focus from direction navigation
				enter = {
					if (danmakuSettingsExpanded || danmakuListVisible) FocusRequester.Cancel else FocusRequester.Default
				}
			},
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(32.dp)
			.onFocusChanged { barFocused = it.isFocused }
			.focusRequester(focusRequester)
			.focusable()
			.onKeyEvent { event ->
					when (event.key) {
						// LEFT/RIGHT scrub with preview + debounced seek (the seek
						// fires on its own after a pause; OK must never seek - it
						// bubbles up and toggles pause like everywhere else)
						Key.DirectionLeft -> {
							if (event.type == KeyEventType.KeyDown && durationMs > 0) {
								scrubTo(((if (previewMs < 0) positionMs else previewMs) - scrubStep(-1)).coerceIn(0L, durationMs))
								onInteraction()
							}
							true
						}
						Key.DirectionRight -> {
							if (event.type == KeyEventType.KeyDown && durationMs > 0) {
								scrubTo(((if (previewMs < 0) positionMs else previewMs) + scrubStep(1)).coerceIn(0L, durationMs))
								onInteraction()
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
				// Seek thumb: plain white bar normally; when the bar has focus it
				// turns into a highlighted cat head so the focus point is obvious
				if (barFocused) {
					CatSeekThumb(
						modifier = Modifier
							.align(Alignment.CenterStart)
							.absoluteOffset(x = maxWidth * fraction - 19.dp)
					)
				} else {
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
				icon = painterResource(R.drawable.ic_bili_danmaku_settings),
				contentDescription = "danmaku settings",
				tint = if (danmakuSettingsExpanded) BiliPink else Color.White,
				onClick = { onDanmakuSettingsExpandedChange(true) },
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DanmakuSettingsPopup(
	danmakuVisible: Boolean,
	danmakuLoaded: Boolean,
	textSizeIdx: Int,
	speedIdx: Int,
	opacityIdx: Int,
	areaIdx: Int,
	onCycle: (Int) -> Unit,
	onToggleDanmaku: () -> Unit,
	onOpenList: () -> Unit,
	onInteraction: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val focusRequester = remember { FocusRequester() }
	var popupHasFocus by remember { mutableStateOf(false) }

	// Pull focus into the popup and KEEP retrying until it actually lands there:
	// during video buffering recomposition can steal/drop the request, and the
	// focus must never be left uncontrolled outside the popup
	LaunchedEffect(popupHasFocus) {
		while (!popupHasFocus) {
			runCatching { focusRequester.requestFocus() }
			delay(100)
		}
	}

	Column(
		modifier = modifier
			.width(230.dp)
			.background(Color(0xF0222222), RoundedCornerShape(10.dp))
			.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
			.onFocusChanged { popupHasFocus = it.hasFocus }
			.focusGroup()
			.focusProperties { exit = { FocusRequester.Cancel } }
			.padding(vertical = 6.dp),
	) {
		// Danmaku on/off toggle (moved here from the bottom bar)
		SettingRow(
			focusRequester = focusRequester,
			label = "弹幕开关",
			value = if (danmakuVisible && danmakuLoaded) "开" else "关",
			onCycle = { onToggleDanmaku(); onInteraction() },
			onDismiss = onDismiss,
		)
		SettingRow(
			label = "字体大小",
			value = danmakuTextSizeLabels[textSizeIdx.coerceIn(0, danmakuTextSizeLabels.lastIndex)],
			onCycle = { onCycle(0); onInteraction() },
			onDismiss = onDismiss,
		)
		SettingRow(
			label = "速度",
			value = danmakuSpeedLabels[speedIdx.coerceIn(0, danmakuSpeedLabels.lastIndex)],
			onCycle = { onCycle(1); onInteraction() },
			onDismiss = onDismiss,
		)
		SettingRow(
			label = "显示区域",
			value = danmakuAreaLabels[areaIdx.coerceIn(0, danmakuAreaLabels.lastIndex)],
			onCycle = { onCycle(2); onInteraction() },
			onDismiss = onDismiss,
		)
		SettingRow(
			label = "不透明度",
			value = "${danmakuOpacities[opacityIdx.coerceIn(0, danmakuOpacities.lastIndex)]}%",
			onCycle = { onCycle(3); onInteraction() },
			onDismiss = onDismiss,
		)
		// Opens the danmaku list side panel
		SettingRow(
			label = "弹幕列表",
			value = "→",
			onCycle = onOpenList,
			onDismiss = onDismiss,
		)
	}
}

@Composable
private fun SettingRow(
	focusRequester: FocusRequester? = null,
	label: String,
	value: String,
	onCycle: () -> Unit,
	onDismiss: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	var modifier = Modifier
		.fillMaxWidth()
		.padding(horizontal = 8.dp, vertical = 2.dp)
		.background(
			if (focused) Color(0x33FFFFFF) else Color.Transparent,
			RoundedCornerShape(8.dp)
		)
		.then(
			if (focused) Modifier.border(2.dp, BiliPink, RoundedCornerShape(8.dp)) else Modifier
		)
		.onFocusChanged { focused = it.isFocused }
		.focusable()
		.onKeyEvent { event ->
			when (event.key) {
				Key.DirectionCenter, Key.Enter -> {
					// KeyUp only: one press = one preset cycle
					if (event.type == KeyEventType.KeyUp) onCycle()
					true
				}
				Key.DirectionLeft, Key.DirectionRight -> {
					// don't steal horizontal navigation while the popup is open
					true
				}
				Key.DirectionUp, Key.DirectionDown -> false
				Key.Back -> {
					if (event.type == KeyEventType.KeyUp) onDismiss()
					true
				}
				else -> false
			}
		}
	if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
	Row(
		modifier = modifier.height(40.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(text = label, color = Color.White, fontSize = 15.sp)
		Spacer(modifier = Modifier.weight(1f))
		Text(text = value, color = BiliPink, fontSize = 15.sp)
	}
}

@Composable
private fun DanmakuListPanel(
	entries: List<DanmakuListEntry>,
	selectedIndex: Int,
	modifier: Modifier = Modifier,
) {
	val listState = rememberLazyListState()

	// Keep the highlighted row visible. The highlight index is owned by the
	// fragment's key handler - no Compose focus involvement at all, so the
	// panel can never lose "selection" and keys cannot wander elsewhere.
	LaunchedEffect(selectedIndex, entries.size) {
		if (entries.isEmpty()) return@LaunchedEffect
		listState.animateScrollToItem(selectedIndex.coerceIn(0, entries.lastIndex))
	}

	Column(
		modifier = modifier
			.width(430.dp)
			.fillMaxHeight(0.72f)
			.background(Color(0xE6111111), RoundedCornerShape(12.dp))
			.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
			.padding(10.dp),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(text = "弹幕列表 (${entries.size})", color = Color.White, fontSize = 16.sp)
			Spacer(modifier = Modifier.weight(1f))
			Text(text = "OK 跳转 · 返回退出", color = Color(0x88FFFFFF), fontSize = 12.sp)
		}

		if (entries.isEmpty()) {
			Text(
				text = "暂无弹幕",
				color = Color(0x88FFFFFF),
				fontSize = 14.sp,
				modifier = Modifier.padding(top = 20.dp).align(Alignment.CenterHorizontally),
			)
		} else {
			LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
				itemsIndexed(entries, key = { _, e -> "${e.timeMs}-${e.text.hashCode()}" }) { index, entry ->
					DanmakuListRow(
						entry = entry,
						selected = index == selectedIndex,
					)
				}
			}
		}
	}
}

@Composable
private fun DanmakuListRow(
	entry: DanmakuListEntry,
	selected: Boolean,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 2.dp, vertical = 2.dp)
			.background(if (selected) Color(0x40FB7299) else Color.Transparent, RoundedCornerShape(8.dp))
			.then(if (selected) Modifier.border(2.dp, BiliPink, RoundedCornerShape(8.dp)) else Modifier)
			.padding(horizontal = 8.dp, vertical = 6.dp),
		verticalAlignment = Alignment.Top,
	) {
		Text(
			text = formatTime(entry.timeMs),
			color = BiliPink,
			fontSize = 13.sp,
			modifier = Modifier.width(52.dp),
		)
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = entry.text,
				color = if (selected) Color.White else Color(0xDDFFFFFF),
				fontSize = 14.sp,
				maxLines = 2,
				overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
			)
			val meta = buildString {
				append(entry.typeLabel)
				entry.sender?.let { append(" · ").append(it.take(10)) }
				entry.sentDateLabel?.let { append(" · ").append(it) }
			}
			Text(text = meta, color = Color(0x88FFFFFF), fontSize = 11.sp)
		}
	}
}

@Composable
private fun CentralPlayButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val focusRequester = remember { FocusRequester() }

	Box(
		modifier = modifier
			.size(96.dp)
			.background(Color(0x80FB7299), CircleShape)
			.border(
				width = if (focused) 4.dp else 2.dp,
				color = if (focused) Color.White else BiliPink,
				shape = CircleShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.focusRequester(focusRequester)
			.autoFocus(focusRequester)
			.focusable()
			.onKeyEvent { event ->
				if (event.type == KeyEventType.KeyUp &&
					(event.key == Key.DirectionCenter || event.key == Key.Enter)
				) {
					onClick()
					true
				} else false
			},
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = painterResource(R.drawable.ic_bili_play),
			contentDescription = "play",
			colorFilter = ColorFilter.tint(Color.White),
			contentScale = ContentScale.Fit,
			modifier = Modifier.size(44.dp),
		)
	}
}

/**
 * Kawaii cat head used as the seek thumb while the progress bar holds focus:
 * pink round face, pointed ears, blush cheeks, big eyes and a tiny smile.
 */
@Composable
private fun CatSeekThumb(modifier: Modifier = Modifier) {
	Canvas(modifier = modifier.size(38.dp)) {
		val w = size.width
		val h = size.height
		val faceColor = BiliPink
		val faceCenter = Offset(w * 0.5f, h * 0.58f)
		val faceRadius = w * 0.30f

		// ears (behind the face): triangles with rounded look via slightly inset tips
		val earPath = Path().apply {
			moveTo(w * 0.22f, h * 0.52f)
			lineTo(w * 0.16f, h * 0.14f)
			lineTo(w * 0.46f, h * 0.32f)
			close()
			moveTo(w * 0.78f, h * 0.52f)
			lineTo(w * 0.84f, h * 0.14f)
			lineTo(w * 0.54f, h * 0.32f)
			close()
		}
		drawPath(earPath, faceColor)

		// inner ears (lighter)
		val innerEar = Color(0x66FFFFFF)
		drawPath(
			Path().apply {
				moveTo(w * 0.26f, h * 0.46f)
				lineTo(w * 0.23f, h * 0.22f)
				lineTo(w * 0.40f, h * 0.33f)
				close()
			},
			innerEar,
		)
		drawPath(
			Path().apply {
				moveTo(w * 0.74f, h * 0.46f)
				lineTo(w * 0.77f, h * 0.22f)
				lineTo(w * 0.60f, h * 0.33f)
				close()
			},
			innerEar,
		)

		// face
		drawCircle(faceColor, radius = faceRadius, center = faceCenter)

		// eyes (big and shiny, dark with white sparkle)
		val eyeY = faceCenter.y + h * 0.02f
		val eyeDx = w * 0.115f
		drawCircle(Color(0xFF3B2B33), radius = w * 0.055f, center = Offset(faceCenter.x - eyeDx, eyeY))
		drawCircle(Color(0xFF3B2B33), radius = w * 0.055f, center = Offset(faceCenter.x + eyeDx, eyeY))
		drawCircle(Color.White, radius = w * 0.020f, center = Offset(faceCenter.x - eyeDx - w * 0.012f, eyeY - w * 0.015f))
		drawCircle(Color.White, radius = w * 0.020f, center = Offset(faceCenter.x + eyeDx - w * 0.012f, eyeY - w * 0.015f))

		// blush cheeks
		drawCircle(Color(0x59FFFFFF), radius = w * 0.055f, center = Offset(faceCenter.x - w * 0.215f, eyeY + h * 0.055f))
		drawCircle(Color(0x59FFFFFF), radius = w * 0.055f, center = Offset(faceCenter.x + w * 0.215f, eyeY + h * 0.055f))

		// smile: small "w" mouth
		val mouthY = eyeY + h * 0.10f
		val stroke = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
		drawArc(
			color = Color(0xFF3B2B33),
			startAngle = 20f, sweepAngle = 140f, useCenter = false,
			topLeft = Offset(faceCenter.x - w * 0.055f, mouthY - h * 0.035f),
			size = Size(w * 0.055f, h * 0.05f),
			style = stroke,
		)
		drawArc(
			color = Color(0xFF3B2B33),
			startAngle = 20f, sweepAngle = 140f, useCenter = false,
			topLeft = Offset(faceCenter.x, mouthY - h * 0.035f),
			size = Size(w * 0.055f, h * 0.05f),
			style = stroke,
		)
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
				if (event.type == KeyEventType.KeyUp &&
					(event.key == Key.DirectionCenter || event.key == Key.Enter)
				) {
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
