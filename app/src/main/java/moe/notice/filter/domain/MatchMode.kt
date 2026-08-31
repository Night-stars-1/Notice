package moe.notice.filter.domain

enum class MatchMode(val id: String) {
    CONTAINS_ANY("contains_any"),
    CONTAINS_ALL("contains_all"),
    NOT_CONTAINS_ANY("not_contains_any"),
    NOT_CONTAINS_ALL("not_contains_all"),
    CONTAINS_A_NOT_B("contains_a_not_b"),
    REGEX("regex"),
    ALL_CONTENT("all_content");

    companion object {
        fun fromId(id: String): MatchMode =
            entries.firstOrNull { it.id == id } ?: CONTAINS_ANY
    }
}
