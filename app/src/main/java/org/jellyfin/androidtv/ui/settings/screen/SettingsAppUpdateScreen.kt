package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.data.repository.AppUpdateRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsAppUpdateScreen() {
	val context = LocalContext.current
	val updateRepository = koinInject<AppUpdateRepository>()
	val coroutineScope = rememberCoroutineScope()

	val isChecking by updateRepository.isChecking.collectAsState()
	val updateInfo by updateRepository.updateInfo.collectAsState()
	val downloadProgress by updateRepository.downloadProgress.collectAsState()
	val downloadMessage by updateRepository.downloadMessage.collectAsState()

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

		// Show update available
		if (updateInfo?.updateAvailable == true && downloadProgress == -1) {
			item {
				ListSection(
					headingContent = { Text("新版本可用") },
					captionContent = {
						val info = updateInfo!!
						Text("版本: ${info.appVersion}  大小: ${formatSize(info.downloadSize)}")
					},
				)
			}

			item {
				ListButton(
					headingContent = { Text("下载并安装") },
					captionContent = { Text(downloadMessage.ifEmpty { "下载完成后将自动打开安装界面" }) },
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

		// Download progress
		if (downloadProgress >= 0 && downloadProgress <= 100) {
			item {
				ListSection(
					headingContent = { Text("下载进度") },
					captionContent = { Text("$downloadProgress%  $downloadMessage") },
				)
			}
		}
	}
}

private fun formatSize(bytes: Long): String {
	return when {
		bytes < 1024 -> "$bytes B"
		bytes < 1024 * 1024 -> "${bytes / 1024} KB"
		else -> "${bytes / (1024 * 1024)} MB"
	}
}
