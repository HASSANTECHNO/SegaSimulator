package ir.segasim.data

import android.content.Context

/** تنظیمات ساده‌ی کاربر (فعلاً فقط حالت تم). */
class SettingsStore(private val context: Context) {

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isDark(): Boolean = prefs().getBoolean(KEY_DARK, true)

    fun setDark(value: Boolean) {
        prefs().edit().putBoolean(KEY_DARK, value).apply()
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
    }
}
