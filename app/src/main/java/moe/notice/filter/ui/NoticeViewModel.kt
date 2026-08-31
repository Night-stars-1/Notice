package moe.notice.filter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.notice.filter.ModuleStatus
import moe.notice.filter.R
import moe.notice.filter.TestNotifier
import moe.notice.filter.data.AppCatalog
import moe.notice.filter.data.AppearanceRepository
import moe.notice.filter.data.InstalledApp
import moe.notice.filter.data.LogRepository
import moe.notice.filter.data.RuleRepository
import moe.notice.filter.data.SpamDeltaWriter
import moe.notice.filter.data.SpamLabel
import moe.notice.filter.data.SpamLabelRepository
import moe.notice.filter.domain.Appearance
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.DarkMode
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.domain.SpamModel
import moe.notice.filter.domain.SpamTuner

class NoticeViewModel(application: Application) : AndroidViewModel(application) {
    private val rules = RuleRepository(application)
    private val logs = LogRepository.get(application)
    private val catalog = AppCatalog(application)
    private val spamLabels = SpamLabelRepository(application)
    private val appearanceRepo = AppearanceRepository(application)

    val config: StateFlow<FilterConfig> = rules.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, rules.config.value)

    val records: StateFlow<List<NotificationRecord>> = logs.items

    val appearance: StateFlow<Appearance> = appearanceRepo.appearance

    /** User spam/ham labels keyed by record id. */
    val labels: StateFlow<Map<String, SpamLabel>> = spamLabels.labels

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    val moduleStatus: StateFlow<ModuleStatus.Info> = ModuleStatus.state

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    /** String resource ids of one-off messages for the UI to show (e.g. save refused). */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = catalog.load()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ModuleStatus.state.collect { rules.attachRemote(ModuleStatus.remotePrefs()) }
        }
        viewModelScope.launch(Dispatchers.Default) {
            spamLabels.labels.drop(1).collectLatest { retune(it.values) }
        }
    }

    /** Refits the tuning delta from all labels and ships it to system_server. */
    private fun retune(labels: Collection<SpamLabel>) {
        val base = SpamModel.bundled() ?: return
        val delta = SpamTuner.fit(base, labels.map { SpamTuner.Sample(it.text, it.spam) })
        if (SpamDeltaWriter.write(delta)) {
            report(rules.setSpamDeltaVersion(System.currentTimeMillis()))
        } else {
            _messages.tryEmit(R.string.save_need_active)
        }
    }

    /** null clears the label. */
    fun setLabel(record: NotificationRecord, spam: Boolean?) {
        if (spam == null) spamLabels.remove(record.id) else spamLabels.set(record, spam)
    }

    fun clearLabels() = spamLabels.clear()

    fun setDarkMode(mode: DarkMode) = appearanceRepo.setDarkMode(mode)

    fun setDynamicColor(enabled: Boolean) = appearanceRepo.setDynamicColor(enabled)

    fun setThemeColor(id: String) = appearanceRepo.setThemeColor(id)

    fun setSpamExcludedPackages(packages: List<String>) {
        report(rules.setSpamExcludedPackages(packages))
    }

    fun setEnabled(enabled: Boolean) {
        report(rules.setEnabled(enabled))
    }

    fun setLogEnabled(enabled: Boolean) {
        report(rules.setLogEnabled(enabled))
    }

    fun setSpamEnabled(enabled: Boolean) {
        report(rules.setSpamEnabled(enabled))
    }

    fun setSpamThreshold(threshold: Float) {
        report(rules.setSpamThreshold(threshold))
    }

    fun saveRule(rule: BlockRule): Boolean = report(rules.upsert(rule))

    fun deleteRule(id: String) {
        report(rules.delete(id))
    }

    fun toggleRule(id: String, enabled: Boolean) {
        report(rules.toggleRule(id, enabled))
    }

    private fun report(saved: Boolean): Boolean {
        if (!saved) _messages.tryEmit(R.string.save_need_active)
        return saved
    }

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
