package moe.notice.filter.domain

/** Applies the spam model with a threshold; the synthetic [rule] is what shows up in logs. */
object SpamJudge {
    /** Normalised texts shorter than this are not judged (too little signal). */
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
     * Messages that must never be blocked by the model regardless of score: one-time codes.
     * The training corpus has no genuine OTP traffic, so this is a hard safety net.
     * Matched against the lowercased raw text.
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
