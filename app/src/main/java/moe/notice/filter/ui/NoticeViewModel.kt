package moe.notice.filter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.notice.filter.ModuleStatus
import moe.notice.filter.R
import moe.notice.filter.TestNotifier
import moe.notice.filter.data.AppCatalog
import moe.notice.filter.data.InstalledApp
import moe.notice.filter.data.LogRepository
import moe.notice.filter.data.RuleRepository
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.NotificationRecord

class NoticeViewModel(application: Application) : AndroidViewModel(application) {
    private val rules = RuleRepository(application)
    private val logs = LogRepository.get(application)
    private val catalog = AppCatalog(application)

    val config: StateFlow<FilterConfig> = rules.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, rules.config.value)

    val records: StateFlow<List<NotificationRecord>> = logs.items

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    val moduleActive: Boolean = ModuleStatus.isActive()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = catalog.load()
        }
    }

    fun setEnabled(enabled: Boolean) = rules.setEnabled(enabled)

    fun setLogEnabled(enabled: Boolean) = rules.setLogEnabled(enabled)

    fun saveRule(rule: BlockRule) = rules.upsert(rule)

    fun deleteRule(id: String) = rules.delete(id)

    fun toggleRule(id: String, enabled: Boolean) = rules.toggleRule(id, enabled)

    fun clearLogs() = logs.clear()

    fun labelFor(packageName: String): String {
        val cached = _apps.value.firstOrNull { it.packageName == packageName }
        if (cached != null) return cached.label
        return catalog.labelFor(packageName)
    }

    fun sendTest(keyword: String) {
        TestNotifier.send(getApplication(), keyword)
    }
}
