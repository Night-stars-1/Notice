package moe.notice.filter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeLineTest {
    @Test
    fun parsesAllowWithScore() {
        val line = JudgeLine.parse(
            "judge enabled=true spamEnabled=true rules={test/off/contains_any/[eee]} pkg=com.tencent.mm " +
                "result=allow spam=0.026 text=王亦航: 8月份我爸来了几天",
        )
        assertNotNull(line)
        assertEquals("com.tencent.mm", line!!.pkg)
        assertFalse(line.blocked)
        assertNull(line.ruleName)
        assertEquals(0.026f, line.score!!, 1e-6f)
        assertFalse(line.protected)
        assertEquals("王亦航: 8月份我爸来了几天", line.text)
    }

    @Test
    fun parsesBlockWithRuleAndProtectedFlag() {
        val line = JudgeLine.parse(
            "judge enabled=true spamEnabled=true rules={(none)} pkg=com.taobao.taobao " +
                "result=block:智能识别骚扰 spam=0.999(protected) text=移不动 促销",
        )!!
        assertTrue(line.blocked)
        assertEquals("智能识别骚扰", line.ruleName)
        assertTrue(line.protected)
        assertEquals(0.999f, line.score!!, 1e-6f)
    }

    @Test
    fun parsesWithoutSpamScoreAndMultilineText() {
        val line = JudgeLine.parse(
            "judge enabled=false spamEnabled=false rules={a/on/regex/[x{2}]} pkg=a.b result=allow text=第一行\n第二行",
        )!!
        assertNull(line.score)
        assertEquals("第一行\n第二行", line.text)
    }

    @Test
    fun nonJudgeLinesReturnNull() {
        assertNull(JudgeLine.parse("config enabled=true"))
        assertNull(JudgeLine.parse("judge something else"))
    }
}
