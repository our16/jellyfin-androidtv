package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.plugin.UiPluginPreferences
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsUiPluginScreen() {
	val pluginPreferences = koinInject<UiPluginPreferences>()
	var activePluginId by remember { mutableStateOf(pluginPreferences.getActivePluginId()) }

	SettingsColumn {
		item {
			ListSection(
				headingContent = { Text("界面风格") },
				captionContent = { Text("选择应用的 UI 风格，更改后需要重启应用生效。") },
			)
		}

		item {
			ListButton(
				headingContent = { Text("默认 Jellyfin") },
				captionContent = {
					Text(if (activePluginId == "default") "当前使用" else "Leanback + Compose 混合界面")
				},
				onClick = {
					pluginPreferences.setActivePluginId("default")
					activePluginId = "default"
				},
				modifier = Modifier.fillMaxWidth()
			)
		}

		item {
			ListButton(
				headingContent = { Text("Bilibili 风格") },
				captionContent = {
					Text(if (activePluginId == "bilibili") "当前使用" else "现代化纯 Compose 界面")
				},
				onClick = {
					pluginPreferences.setActivePluginId("bilibili")
					activePluginId = "bilibili"
				},
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}
