package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamJudgeTest {
    private fun constantModel(bias: Float) =
        SpamModel(buckets = 8, ngramMin = 1, ngramMax = 1, bias = bias, weights = FloatArray(8))

    @Test
    fun skipsShortText() {
        assertNull(SpamJudge.judge(constantModel(10f), 0.5f, "abc"))
        assertNull(SpamJudge.judge(constantModel(10f), 0.5f, "  a  b  "))
        assertNotNull(SpamJudge.judge(constantModel(10f), 0.5f, "abcd"))
    }

    @Test
    fun blocksAtOrAboveThreshold() {
        val high = SpamJudge.judge(constantModel(10f), 0.9f, "hello world")!!
        assertTrue(high.block)
        assertTrue(high.score > 0.99f)

        val low = SpamJudge.judge(constantModel(-10f), 0.9f, "hello world")!!
        assertFalse(low.block)
        assertTrue(low.score < 0.01f)
    }

    @Test
    fun neverBlocksVerificationCodes() {
        val model = constantModel(10f)
        for (text in listOf(
            "【某银行】您的验证码是 483920，5分钟内有效",
            "Your verification code is 123456",
            "OTP: 998877 expires in 10 minutes",
            "动态密码：445566，请勿泄露",
        )) {
            val v = SpamJudge.judge(model, 0.5f, text)
            assertNotNull(text, v)
            assertFalse(text, v!!.block)
            assertTrue(text, v.protected)
        }
        assertFalse(SpamJudge.judge(model, 0.5f, "限时特惠，点击领取")!!.protected)
    }

    @Test
    fun syntheticRuleIsStable() {
        assertEquals("spam_model", SpamJudge.rule.id)
        assertEquals("智能识别骚扰", SpamJudge.rule.name)
        assertTrue(SpamJudge.rule.enabled)
    }
}
