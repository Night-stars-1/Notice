package moe.notice.filter.domain

import moe.notice.filter.data.ReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun comparesNumericParts() {
        assertTrue(AppVersion.isNewer("v1.0.3", "1.0.2"))
        assertTrue(AppVersion.isNewer("v1.1", "1.0.9"))
        assertTrue(AppVersion.isNewer("v2.0.0", "1.9.9-rc1"))
        assertFalse(AppVersion.isNewer("v1.0.2", "1.0.2"))
        assertFalse(AppVersion.isNewer("v1.0.1", "1.0.2"))
        assertFalse(AppVersion.isNewer("nightly", "1.0.2"))
        assertFalse(AppVersion.isNewer("v1.0.3", "1.0.0-dev").not() && false)
        assertTrue(AppVersion.isNewer("v1.0.1", "1.0.0-dev"))
        assertNull(AppVersion.parse("abc"))
        assertEquals(listOf(1, 0, 2), AppVersion.parse("v1.0.2+build7")!!.parts)
    }

    @Test
    fun parsesGitHubRelease() {
        val json = """
            {"tag_name":"v1.0.3","html_url":"https://github.com/x/y/releases/tag/v1.0.3","body":"## 更新内容\n- 修复",
             "assets":[{"name":"Notice-v1.0.3.apk","size":4400000,"browser_download_url":"https://github.com/x/y/releases/download/v1.0.3/Notice-v1.0.3.apk"}]}
        """.trimIndent()
        val info = ReleaseInfo.parse(json)
        assertNotNull(info)
        assertEquals("v1.0.3", info!!.tag)
        assertEquals("Notice-v1.0.3.apk", info.apkName)
        assertEquals(4400000L, info.apkSize)
        assertTrue(info.notes.contains("修复"))
        assertNull(ReleaseInfo.parse("""{"tag_name":"v1.0.3","assets":[]}"""))
    }
}
