package org.jellyfin.androidtv.ui.plugin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

object UiPluginRegistry {
    private val plugins = mutableMapOf<String, UiPlugin>()

    fun registerPlugin(plugin: UiPlugin) {
        plugins[plugin.id] = plugin
    }

    fun getAvailablePlugins(): List<UiPlugin> {
        return plugins.values.toList()
    }
}