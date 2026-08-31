package moe.notice.filter.domain

enum class DarkMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): DarkMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 仅应用内的外观设置；存储在本地，绝不发送给 system_server。 */
data class Appearance(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColor: String = DEFAULT_THEME_COLOR,
) {
    companion object {
        const val DEFAULT_THEME_COLOR = "blue"
    }
}
