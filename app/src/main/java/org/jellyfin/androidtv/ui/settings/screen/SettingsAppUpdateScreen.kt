package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.data.repository.AppUpdateRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val UpdateAccent = Color(0xFF00A4DC)

@Composable
private fun DownloadProgressRing(progress: Float, modifier: Modifier = Modifier) {
	Box(modifier = modifier.size(140.dp), contentAlignment = Alignment.Center) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val stroke = 10.dp.toPx()
			val inset = stroke / 2
			drawArc(
				color = Color(0x33FFFFFF),
				startAngle = 0f,
				sweepAngle = 360f,
				useCenter = false,
				topLeft = Offset(inset, inset),
				size = Size(size.width - stroke, size.height - stroke),
				style = Stroke(width = stroke, cap = StrokeCap.Round),
			)
			drawArc(
				color = UpdateAccent,
				startAngle = -90f,
				sweepAngle = 360f * progress.coerceIn(0f, 1f),
				useCenter = false,
				topLeft = Offset(inset, inset),
				size = Size(size.width - stroke, size.height - stroke),
				style = Stroke(width = stroke, cap = StrokeCap.Round),
			)
		}
		Text(
			text = "${(progress * 100).toInt()}%",
			color = Color.White,
			fontSize = 28.sp,
		)
	}
}

@Composable
fun SettingsAppUpdateScreen() {
	val context = LocalContext.current
	val updateRepository = koinInject<AppUpdateRepository>()
	val coroutineScope = rememberCoroutineScope()

	val isChecking by updateRepository.isChecking.collectAsState()
	val updateInfo by updateRepository.updateInfo.collectAsState()
	val downloadProgress by updateRepository.downloadProgress.collectAsState()
	val downloadMessage by updateRepository.downloadMessage.collectAsState()
	val downloadedApk by updateRepository.downloadedApk.collectAsState()
	val isInstalling by updateRepository.isInstalling.collectAsState()

	var showInstallConfirm by remember { mutableStateOf(false) }

	// Download finished -> pop the confirm dialog automatically (single confirmation step)
	LaunchedEffect(downloadedApk) {
		if (downloadedApk != null) showInstallConfirm = true
	}

	Box(modifier = Modifier.fillMaxSize()) {
		SettingsColumn {
			item {
				ListSection(
					headingContent = { Text("应用更新") },
					captionContent = { Text("当前版本: ${BuildConfig.VERSION_NAME}") },
				)
			}

			item {
				ListButton(
					headingContent = {
						Text(if (isChecking) "检查中..." else "检查更新")
					},
					captionContent = {
						Text(downloadMessage.ifEmpty { "点击检查服务器是否有新版本" })
					},
					onClick = {
						if (!isInstalling) {
							coroutineScope.launch {
								updateRepository.checkForUpdate()
							}
						}
					},
					modifier = Modifier.fillMaxWidth()
				)
			}

			// Update available: version info + changelog + download action
			if (updateInfo?.updateAvailable == true && downloadedApk == null && downloadProgress == -1 && !isInstalling) {
				item {
					ListSection(
						headingContent = { Text("发现新版本") },
						captionContent = {
							val info = updateInfo!!
							Text("版本: ${info.appVersion}  大小: ${formatSize(info.downloadSize)}")
						},
					)
				}

				val changelog = pickChangelog(updateInfo!!.changelog)
				if (changelog != null) {
					item {
						ListSection(
							headingContent = { Text("更新内容") },
							captionContent = { Text(changelog) },
						)
					}
				}

				item {
					ListButton(
						headingContent = { Text("下载更新") },
						captionContent = { Text("下载完成后需手动确认安装") },
						onClick = {
							coroutineScope.launch {
								updateInfo?.let { info ->
									updateRepository.downloadApk(context, info)
								}
							}
						},
						modifier = Modifier.fillMaxWidth()
					)
				}
			}

			// Download progress: ring with percentage in the center
			if (downloadProgress >= 0) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 16.dp),
						contentAlignment = Alignment.Center,
					) {
						DownloadProgressRing(progress = downloadProgress / 100f)
					}
				}
			}
		}

		// Install confirmation overlay: single confirm button, warns that the app will close
		if (showInstallConfirm && downloadedApk != null) {
			InstallConfirmOverlay(
				newVersion = updateInfo?.appVersion ?: "",
				sizeBytes = updateInfo?.downloadSize ?: 0,
				onConfirm = {
					showInstallConfirm = false
					updateRepository.installDownloadedApk(context)
				},
				onDismiss = { showInstallConfirm = false },
			)
		}

		// Installing status: shown instead of any action buttons (the app may be killed)
		if (isInstalling) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0x99000000)),
				contentAlignment = Alignment.Center,
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier
						.background(Color(0xF0222222), RoundedCornerShape(12.dp))
						.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
						.padding(horizontal = 32.dp, vertical = 24.dp),
				) {
					Text(text = "正在安装 ${updateInfo?.appVersion ?: ""}", color = Color.White, fontSize = 20.sp)
					Text(
						text = "应用即将关闭以完成更新\n完成后将自动重启进入新版本",
						color = Color(0xCCFFFFFF),
						fontSize = 16.sp,
					)
				}
			}
		}
	}
}

@Composable
private fun InstallConfirmOverlay(
	newVersion: String,
	sizeBytes: Long,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	val confirmFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) { confirmFocus.requestFocus() }

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0x99000000)),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(14.dp),
			modifier = Modifier
				.width(420.dp)
				.background(Color(0xF0222222), RoundedCornerShape(12.dp))
				.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
				.padding(horizontal = 28.dp, vertical = 24.dp),
		) {
			Text(text = "确认安装 $newVersion", color = Color.White, fontSize = 20.sp)
			Text(
				text = "更新包已就绪（${formatSize(sizeBytes)}）。\n\n安装过程中应用会关闭，完成后将自动重启进入新版本。",
				color = Color(0xCCFFFFFF),
				fontSize = 16.sp,
			)
			Spacer(modifier = Modifier.height(4.dp))
			InstallConfirmButton(
				focusRequester = confirmFocus,
				label = "确认安装",
				onClick = onConfirm,
			)
			InstallConfirmButton(
				focusRequester = null,
				label = "取消",
				onClick = onDismiss,
			)
		}
	}
}

@Composable
private fun InstallConfirmButton(
	focusRequester: androidx.compose.ui.focus.FocusRequester?,
	label: String,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	var modifier = Modifier
		.width(200.dp)
		.background(
			if (focused) UpdateAccent else Color(0x22FFFFFF),
			RoundedCornerShape(8.dp)
		)
		.onFocusChanged { focused = it.isFocused }
		.focusable()
		.onKeyEvent { event ->
			if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
				onClick()
				true
			} else false
		}
	if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
	Box(modifier = modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
		Text(text = label, color = Color.White, fontSize = 17.sp)
	}
}

private fun pickChangelog(changelog: Map<String, String>): String? {
	if (changelog.isEmpty()) return null
	return changelog["zh-CN"]
		?: changelog["zh-Hans"]
		?: changelog["en-US"]
		?: changelog.values.firstOrNull()
}

private fun formatSize(bytes: Long): String {
	return when {
		bytes < 1024 -> "$bytes B"
		bytes < 1024 * 1024 -> "${bytes / 1024} KB"
		else -> "${bytes / (1024 * 1024)} MB"
	}
}
