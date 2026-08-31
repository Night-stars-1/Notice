package moe.notice.filter.domain

/** 按阈值应用垃圾短信模型；日志中显示的是合成的 [rule]。 */
object SpamJudge {
    /** 归一化后短于此长度的文本不做判定（信号太少）。 */
    const val MIN_LENGTH = 4
    const val RULE_ID = "spam_model"
    const val RULE_NAME = "智能识别骚扰"

    val rule: BlockRule = BlockRule(
        id = RULE_ID,
        name = RULE_NAME,
        enabled = true,
        mode = MatchMode.ALL_CONTENT,
    )

    /**
     * 无论得分如何都绝不能被模型拦截的消息：一次性验证码。
     * 训练语料中没有真实的 OTP 流量，因此这是一道硬性安全网。
     * 与转为小写后的原始文本进行匹配。
     */
    private val PROTECTED_MARKERS = listOf(
        "验证码", "校验码", "动态码", "动态密码", "一次性密码", "驗證碼",
        "verification code", "verify code", "security code", "passcode", "one-time password", "otp",
    )

    data class Verdict(val score: Float, val block: Boolean, val protected: Boolean = false)

    fun judge(model: SpamModel, threshold: Float, text: String): Verdict? {
        if (SpamFeatures.normalize(text).length < MIN_LENGTH) return null
        val score = model.score(text)
        if (isProtected(text)) return Verdict(score = score, block = false, protected = true)
        return Verdict(score = score, block = score >= threshold)
    }

    fun isProtected(text: String): Boolean {
        val lower = text.lowercase()
        return PROTECTED_MARKERS.any { lower.contains(it) }
    }
}
