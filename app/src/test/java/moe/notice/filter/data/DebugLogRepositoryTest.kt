package moe.notice.filter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugLogRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun appendsNewestFirstAndSurvivesReload() {
        val file = folder.newFile("debug.txt")
        val repo = DebugLogRepository(file)
        repo.append(1_000, 4, "first")
        repo.append(2_000, 6, "boom\twith tab", trace = "java.lang.RuntimeException\n\tat a.b(c.kt:1)")
        val items = repo.items.value
        assertEquals(listOf("boom\twith tab", "first"), items.map { it.message })
        assertTrue(items[0].isError)
        val reloaded = DebugLogRepository(file).items.value
        assertEquals(items, reloaded)
        assertEquals("java.lang.RuntimeException\n\tat a.b(c.kt:1)", reloaded[0].trace)
    }

    @Test
    fun trimsToMaxLines() {
        val file = folder.newFile("trim.txt")
        val repo = DebugLogRepository(file)
        // 超过 MAX_LINES + TRIM_SLACK 时一次性裁回 MAX_LINES，避免每行都重写整个文件。
        val total = DebugLogRepository.MAX_LINES + DebugLogRepository.TRIM_SLACK + 1
        repeat(total) { repo.append(it.toLong(), 4, "line $it") }
        assertEquals(DebugLogRepository.MAX_LINES, repo.items.value.size)
        assertEquals("line ${total - 1}", repo.items.value.first().message)
        assertEquals(DebugLogRepository.MAX_LINES, DebugLogRepository(file).items.value.size)
    }

    @Test
    fun judgeLinesAreFlagged() {
        val repo = DebugLogRepository(folder.newFile("j.txt"))
        repo.append(1, 4, "judge enabled=true pkg=a result=allow text=x")
        repo.append(2, 4, "config enabled=true")
        assertTrue(repo.items.value[1].isJudge)
        assertTrue(!repo.items.value[0].isJudge)
    }

    @Test
    fun clearEmptiesFile() {
        val file = folder.newFile("c.txt")
        val repo = DebugLogRepository(file)
        repo.append(1, 4, "x")
        repo.clear()
        assertTrue(repo.items.value.isEmpty())
        assertEquals("", file.readText())
    }
}
