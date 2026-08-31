package moe.notice.filter.domain

enum class DarkMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): DarkMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** App-only look & feel; stored locally, never sent to system_server. */
data class Appearance(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColor: String = DEFAULT_THEME_COLOR,
) {
    companion object {
        const val DEFAULT_THEME_COLOR = "blue"
    }
}
