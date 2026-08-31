package moe.notice.filter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.notice.filter.domain.Appearance
import moe.notice.filter.domain.DarkMode

class AppearanceRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(read())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setDarkMode(mode: DarkMode) = update { it.copy(darkMode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setThemeColor(id: String) = update { it.copy(themeColor = id) }

    private fun update(block: (Appearance) -> Appearance) {
        val next = block(_appearance.value)
        prefs.edit()
            .putString(KEY_DARK_MODE, next.darkMode.id)
            .putBoolean(KEY_DYNAMIC_COLOR, next.dynamicColor)
            .putString(KEY_THEME_COLOR, next.themeColor)
            .apply()
        _appearance.value = next
    }

    private fun read(): Appearance = Appearance(
        darkMode = DarkMode.fromId(prefs.getString(KEY_DARK_MODE, null)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
        themeColor = prefs.getString(KEY_THEME_COLOR, null) ?: Appearance.DEFAULT_THEME_COLOR,
    )

    private companion object {
        const val NAME = "appearance"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_THEME_COLOR = "theme_color"
    }
}
