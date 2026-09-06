package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
fun SettingsAppUpdateScreen() {
	val context = LocalContext.current
	val updateRepository = koinInject<AppUpdateRepository>()
	val coroutineScope = rememberCoroutineScope()

	val isChecking by updateRepository.isChecking.collectAsState()
	val updateInfo by updateRepository.updateInfo.collectAsState()
	val downloadProgress by updateRepository.downloadProgress.collectAsState()
	val downloadMessage by updateRepository.downloadMessage.collectAsState()
	val downloadedApk by updateRepository.downloadedApk.collectAsState()

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
					if (!isChecking) {
						coroutineScope.launch {
							updateRepository.checkForUpdate()
						}
					}
				},
				modifier = Modifier.fillMaxWidth()
			)
		}

		// Update available: version info + changelog + download action
		if (updateInfo?.updateAvailable == true && downloadedApk == null && downloadProgress == -1) {
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

		// Download progress with a visual bar
		if (downloadProgress >= 0) {
			item {
				ListSection(
					headingContent = { Text("下载进度") },
					captionContent = { Text("$downloadProgress%") },
				)
			}
			item {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 8.dp)
						.height(8.dp)
						.background(Color(0x33FFFFFF))
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth(downloadProgress / 100f)
							.fillMaxHeight()
							.background(UpdateAccent)
					)
				}
			}
		}

		// Download finished: wait for explicit user confirmation to install
		if (downloadedApk != null) {
			item {
				ListButton(
					headingContent = { Text("开始安装") },
					captionContent = {
						Text("版本 ${updateInfo?.appVersion ?: ""}  点击后将弹出系统安装确认")
					},
					onClick = {
						updateRepository.installDownloadedApk(context)
					},
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
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
