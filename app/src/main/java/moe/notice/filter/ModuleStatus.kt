package moe.notice.filter

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activation state of the module, fed by the Xposed service the framework hands to the app
 * (see [NoticeApplication]). Modern modules are not hooked by themselves, so this is the only
 * way to know whether the framework is present.
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

    /** Framework-backed preferences; null when the module is not active or the framework lacks remote support. */
    fun remotePrefs(): SharedPreferences? {
        val current = service ?: return null
        return try {
            current.getRemotePreferences(FilterPrefs.NAME)
        } catch (t: Throwable) {
            Log.w("Notice", "remote preferences unavailable", t)
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
