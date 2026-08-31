package moe.notice.filter.data

import android.content.Context
import android.content.pm.ApplicationInfo

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
        if (packageName.isBlank()) return packageName
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(context.packageManager).toString()
        }.getOrDefault(packageName)
    }
}
