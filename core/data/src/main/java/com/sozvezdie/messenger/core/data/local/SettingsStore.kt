package com.sozvezdie.messenger.core.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Небольшие настройки на телефоне: тема, обои и черновики.
 *
 * Они не синхронизируются между устройствами: это личные удобства,
 * поэтому хватает SharedPreferences.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("sozvezdie_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    val themeFlow: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _theme.value = theme
    }

    // Обои храним строкой: "gradient:2" или "file:/путь/к/фото.jpg".
    fun getWallpaper(chatId: String): String =
        prefs.getString("wallpaper_$chatId", "") ?: ""

    fun setWallpaper(chatId: String, value: String) {
        prefs.edit().putString("wallpaper_$chatId", value).apply()
    }

    fun getDraft(chatId: String): String =
        prefs.getString("draft_$chatId", "") ?: ""

    fun setDraft(chatId: String, text: String) {
        prefs.edit().putString("draft_$chatId", text).apply()
    }

    // После выхода из аккаунта личные черновики и обои больше не нужны.
    fun clearLocalSettings() {
        prefs.edit().clear().apply()
        _theme.value = "system"
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
