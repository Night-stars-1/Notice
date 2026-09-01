package moe.notice.filter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import moe.notice.filter.BuildConfig
import org.json.JSONObject

/** GitHub Release 的关键信息。 */
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
) {
    companion object {
        /** 解析 `GET /repos/{owner}/{repo}/releases/latest` 的响应；没有 APK 资产时返回 null。 */
        fun parse(json: String): ReleaseInfo? {
            val obj = JSONObject(json)
            val assets = obj.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return ReleaseInfo(
                        tag = obj.optString("tag_name"),
                        notes = obj.optString("body"),
                        htmlUrl = obj.optString("html_url"),
                        apkUrl = asset.optString("browser_download_url"),
                        apkName = name,
                        apkSize = asset.optLong("size"),
                    )
                }
            }
            return null
        }
    }
}

/** 检查 GitHub Releases 上的新版本并下载 APK。全部为阻塞调用，需在 IO 线程执行。 */
class UpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var lastCheckedAt: Long
        get() = prefs.getLong("last_checked_at", 0L)
        set(value) = prefs.edit().putLong("last_checked_at", value).apply()

    /** 最新 Release；仓库无 Release 或无 APK 资产时抛 [IllegalStateException]。 */
    fun fetchLatest(): ReleaseInfo {
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Notice/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            val body = conn.inputStream.bufferedReader().readText()
            return ReleaseInfo.parse(body) ?: throw IllegalStateException("release has no APK asset")
        } finally {
            conn.disconnect()
        }
    }

    /** 下载 APK 到缓存目录；[onProgress] 参数为 0..1（总长未知时为 -1）。 */
    fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name != info.apkName) it.delete() }
        val target = File(dir, info.apkName)
        val tmp = File(dir, info.apkName + ".part")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Notice/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw IllegalStateException("rename failed")
        return target
    }

    /** 交给系统安装器的 Intent（需要 REQUEST_INSTALL_PACKAGES 权限和 FileProvider）。 */
    fun installIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val REPO = "Night-stars-1/Notice"
        const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    }
}
