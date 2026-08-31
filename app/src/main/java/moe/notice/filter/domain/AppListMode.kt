package moe.notice.filter.domain

enum class AppListMode(val id: String) {
    WHITELIST("whitelist"),
    BLACKLIST("blacklist");

    companion object {
        fun fromId(id: String): AppListMode =
            entries.firstOrNull { it.id == id } ?: WHITELIST
    }
}
