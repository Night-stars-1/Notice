package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpamTunerTest {
    private val buckets = 1 shl 12

    private fun model(bias: Float) =
        SpamModel(buckets = buckets, ngramMin = 1, ngramMax = 3, bias = bias, weights = FloatArray(buckets))

    private val spamText = "限时特惠！全场一折起，点击领取红包，回复TD退订"
    private val hamText = "妈妈说周末回家吃饭，记得早点到"
    private val otherText = "会议改到下午三点，地点不变"

    @Test
    fun learnsSpamLabelWithoutMovingUnrelatedText() {
        val base = model(bias = -4f) // everything ham by default
        assertTrue(base.score(spamText) < 0.1f)
        val delta = SpamTuner.fit(base, listOf(SpamTuner.Sample(spamText, spam = true)))
        val tuned = base.withDelta(delta)
        assertTrue("tuned=${tuned.score(spamText)}", tuned.score(spamText) >= 0.9f)
        assertTrue("other=${tuned.score(otherText)}", abs(tuned.score(otherText) - base.score(otherText)) < 0.05f)
    }

    @Test
    fun learnsHamLabel() {
        val base = model(bias = 4f) // everything spam by default
        val delta = SpamTuner.fit(base, listOf(SpamTuner.Sample(hamText, spam = false)))
        val tuned = base.withDelta(delta)
        assertTrue("tuned=${tuned.score(hamText)}", tuned.score(hamText) <= 0.1f)
    }

    @Test
    fun fitsMixedLabelsTogether() {
        val base = model(bias = 0f)
        val delta = SpamTuner.fit(
            base,
            listOf(SpamTuner.Sample(spamText, true), SpamTuner.Sample(hamText, false)),
        )
        val tuned = base.withDelta(delta)
        assertTrue(tuned.score(spamText) >= 0.9f)
        assertTrue(tuned.score(hamText) <= 0.1f)
    }

    @Test
    fun emptyAndShortSamplesProduceEmptyDelta() {
        val base = model(bias = 0f)
        assertTrue(SpamTuner.fit(base, emptyList()).isEmpty)
        assertTrue(SpamTuner.fit(base, listOf(SpamTuner.Sample("嗨", true))).isEmpty)
        assertEquals(buckets, SpamTuner.fit(base, emptyList()).buckets)
    }
}
