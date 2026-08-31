package moe.notice.filter.data

import moe.notice.filter.domain.NotificationDetails
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun repo() = LogRepository(folder.newFile("log.json"))

    private fun LogRepository.post(
        text: String,
        at: Long,
        progress: String = "",
        id: Int = 7,
        tag: String = "",
        pkg: String = "com.example.dl",
    ) = add(
        packageName = pkg,
        title = "下载",
        text = text,
        timestamp = at,
        blocked = false,
        ruleId = null,
        ruleName = null,
        details = NotificationDetails(notificationId = id, tag = tag, progress = progress),
    )

    @Test
    fun progressUpdatesMergeInPlace() {
        val r = repo()
        r.post("其他通知", at = 1_000, id = 1)
        r.post("10%", at = 2_000, progress = "10 / 100")
        r.post("50%", at = 3_000, progress = "50 / 100")
        r.post("100%", at = 4_000, progress = "100 / 100")
        val items = r.items.value
        assertEquals(2, items.size)
        assertEquals("100%", items[0].text)
        assertEquals(2, items[0].updateCount)
        assertEquals(4_000, items[0].timestamp)
        assertEquals("其他通知", items[1].text)
    }

    @Test
    fun keepsOrderWhenNewerRowsExist() {
        val r = repo()
        r.post("10%", at = 1_000, progress = "10 / 100")
        r.post("聊天消息", at = 2_000, id = 2, pkg = "com.example.chat")
        r.post("90%", at = 3_000, progress = "90 / 100")
        val items = r.items.value
        assertEquals(listOf("聊天消息", "90%"), items.map { it.text })
    }

    @Test
    fun nonProgressUpdatesStaySeparate() {
        val r = repo()
        r.post("第一条", at = 1_000)
        r.post("第二条", at = 2_000)
        assertEquals(2, r.items.value.size)
    }

    @Test
    fun staleProgressStartsNewRow() {
        val r = repo()
        r.post("10%", at = 1_000, progress = "10 / 100")
        r.post("10%", at = 1_000 + 11 * 60 * 1000, progress = "10 / 100")
        assertEquals(2, r.items.value.size)
    }

    @Test
    fun updateCountSurvivesReload() {
        val file = folder.newFile("persist.json")
        val r = LogRepository(file)
        r.post("10%", at = 1_000, progress = "10 / 100")
        r.post("20%", at = 2_000, progress = "20 / 100")
        assertEquals(1, LogRepository(file).items.value[0].updateCount)
    }
}
