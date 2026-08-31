package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamExplainerTest {
    private val buckets = 1 shl 12

    private fun bucket(gram: String) = SpamFeatures.fnv1a32(gram, 0, gram.length) and (buckets - 1)

    private fun model(vararg weighted: Pair<String, Float>, bias: Float = -2f): SpamModel {
        val w = FloatArray(buckets)
        for ((gram, weight) in weighted) w[bucket(gram)] += weight
        return SpamModel(buckets, 1, 3, bias, w)
    }

    @Test
    fun findsThePushingAndPullingTerms() {
        val m = model("优惠" to 8f, "领取" to 6f, "会议" to -7f)
        val text = "限时优惠 123，点击领取；会议改期"
        val e = SpamExplainer.explain(m, text)
        assertEquals("优惠", e.positives[0].text)
        assertEquals("领取", e.positives[1].text)
        assertEquals("会议", e.negatives[0].text)
        // range 指向原文（含被归一化去掉的空格和数字）
        assertEquals("优惠", text.substring(e.positives[0].range))
        assertEquals("会议", text.substring(e.negatives[0].range))
    }

    @Test
    fun contributionsSumToTheLogit() {
        val m = model("优惠" to 8f, "领取" to 6f, "会议" to -7f, "点" to 1.5f)
        val text = "限时优惠，点击领取；会议改期"
        val e = SpamExplainer.explain(m, text, top = 100)
        val sum = e.positives.sumOf { it.contribution.toDouble() } + e.negatives.sumOf { it.contribution.toDouble() }
        assertEquals(SpamExplainer.logit(e.score).toDouble(), sum + m.bias, 0.05)
    }

    @Test
    fun emptyTextHasNoTerms() {
        val e = SpamExplainer.explain(model("a" to 1f), "  12 ")
        assertTrue(e.positives.isEmpty() && e.negatives.isEmpty())
    }
}
