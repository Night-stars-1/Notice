package moe.notice.filter

import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 模块的激活状态，由框架交给应用的 Xposed 服务提供（见 [NoticeApplication]）。
 * 现代模块不会 hook 自身，因此这是判断框架是否存在的唯一途径。
 */
object ModuleStatus {
    data class Info(
        val active: Boolean = false,
        val apiVersion: Int = -1,
        val frameworkName: String = "",
        val frameworkVersion: String = "",
        val frameworkVersionCode: Long = 0,
        val scope: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(Info())
    val state: StateFlow<Info> = _state.asStateFlow()

    @Volatile
    private var service: XposedService? = null

    /** 由框架托管的偏好设置；模块未激活或框架不支持远程偏好时为 null。 */
    fun remotePrefs(): SharedPreferences? {
        val current = service ?: return null
        return try {
            current.getRemotePreferences(FilterPrefs.NAME)
        } catch (t: Throwable) {
            Log.w("Notice", "remote preferences unavailable", t)
            null
        }
    }

    /** 打开（必要时创建）模块框架侧数据目录中的文件；未激活时为 null。 */
    fun openRemoteFile(name: String): ParcelFileDescriptor? {
        val current = service ?: return null
        return try {
            current.openRemoteFile(name)
        } catch (t: Throwable) {
            Log.w("Notice", "remote file unavailable: $name", t)
            null
        }
    }

    fun onServiceBound(service: XposedService) {
        this.service = service
        _state.value = try {
            Info(
                active = true,
                apiVersion = service.apiVersion,
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                frameworkVersionCode = service.frameworkVersionCode,
                scope = service.scope,
            )
        } catch (_: Throwable) {
            Info(active = true)
        }
    }

    fun onServiceDied() {
        service = null
        _state.value = Info()
    }
}
