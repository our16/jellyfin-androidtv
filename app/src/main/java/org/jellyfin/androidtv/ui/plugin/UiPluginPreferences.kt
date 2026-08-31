package org.jellyfin.androidtv.ui.plugin

import android.content.Context
import android.content.SharedPreferences
import org.koin.dsl.module

const val UI_PLUGIN_PREF_KEY = "ui_plugin_id"

val uiPluginPreferencesModule = module {
    single<UiPluginPreferences> { UiPluginPreferencesImpl(get()) }
}

interface UiPluginPreferences {
    fun getActivePluginId(): String
    fun setActivePluginId(pluginId: String)
    fun getAvailablePlugins(): List<PluginInfo>
}

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String
)

class UiPluginPreferencesImpl(private val context: Context) : UiPluginPreferences {
    private val prefs: SharedPreferences = context.getSharedPreferences("ui_plugin_prefs", Context.MODE_PRIVATE)

    override fun getActivePluginId(): String {
        return prefs.getString(UI_PLUGIN_PREF_KEY, "default") ?: "default"
    }

    override fun setActivePluginId(pluginId: String) {
        prefs.edit().putString(UI_PLUGIN_PREF_KEY, pluginId).apply()
    }

    override fun getAvailablePlugins(): List<PluginInfo> {
        return UiPluginRegistry.getAvailablePlugins().map { plugin ->
            PluginInfo(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                description = when (plugin.id) {
                    "default" -> "原版 Jellyfin 界面 (Leanback + Compose 混合)"
                    "bilibili" -> "Bilibili 风格现代化界面 (纯 Compose)"
                    else -> "自定义 UI 插件"
                }
            )
        }
    }
}