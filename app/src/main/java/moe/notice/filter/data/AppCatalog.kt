package moe.notice.filter.data

import android.content.Context
import moe.notice.filter.R
import android.content.pm.ApplicationInfo

/** 用于旧记录的框架包名，这些记录捕获到的包名为空。 */
const val SYSTEM_PACKAGE = "android"

/** 旧的日志行捕获系统通知时包名为空；将其显示为 [SYSTEM_PACKAGE]。 */
fun String.orSystemPackage(): String = ifBlank { SYSTEM_PACKAGE }

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

class AppCatalog(private val context: Context) {
    fun load(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString().ifBlank { info.packageName },
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun labelFor(packageName: String): String {
        val pkg = packageName.orSystemPackage()
        return runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            info.loadLabel(context.packageManager).toString()
        }.getOrElse { if (pkg == SYSTEM_PACKAGE) context.getString(R.string.app_system) else pkg }
    }
}
