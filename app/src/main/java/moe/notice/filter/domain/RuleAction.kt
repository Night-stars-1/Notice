package moe.notice.filter.domain

/** 规则命中后的执行行为。 */
enum class RuleAction(val id: String) {
    /** 拦截通知（默认）。 */
    BLOCK("block"),
    /** 直接放行：不再匹配后续规则，也不进行智能识别。 */
    ALLOW("allow"),
    /** 不进行智能识别，但继续匹配后续规则。 */
    SKIP_AI("skip_ai");

    companion object {
        fun fromId(id: String?): RuleAction = entries.firstOrNull { it.id == id } ?: BLOCK
    }
}
