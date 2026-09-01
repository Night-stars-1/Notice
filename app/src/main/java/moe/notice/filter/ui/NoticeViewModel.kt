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
import moe.notice.filter.data.DebugLine
import moe.notice.filter.data.DebugLogRepository
import moe.notice.filter.data.InstalledApp
import moe.notice.filter.data.LogRepository
import moe.notice.filter.data.RuleRepository
import moe.notice.filter.data.SpamDeltaWriter
import moe.notice.filter.data.SpamLabel
import moe.notice.filter.data.SpamLabelRepository
import moe.notice.filter.data.UpdateRepository
import moe.notice.filter.domain.Appearance
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.DarkMode
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.domain.SpamExplainer
import moe.notice.filter.domain.SpamJudge
import moe.notice.filter.domain.SpamModel
import moe.notice.filter.domain.SpamTuner
import moe.notice.filter.domain.UpdateState
import moe.notice.filter.domain.AppVersion
import moe.notice.filter.BuildConfig
import android.content.Context

class NoticeViewModel(application: Application) : AndroidViewModel(application) {
    private val rules = RuleRepository(application)
    private val logs = LogRepository.get(application)
    private val catalog = AppCatalog(application)
    private val spamLabels = SpamLabelRepository(application)
    private val appearanceRepo = AppearanceRepository(application)
    private val debugLog = DebugLogRepository.get(application)
    private val updates = UpdateRepository(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val config: StateFlow<FilterConfig> = rules.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, rules.config.value)

    val records: StateFlow<List<NotificationRecord>> = logs.items

    val appearance: StateFlow<Appearance> = appearanceRepo.appearance

    /** 模块运行日志，最新在最前。 */
    val debugLines: StateFlow<List<DebugLine>> = debugLog.items

    /** 用户的垃圾/正常标注，以记录 id 为键。 */
    val labels: StateFlow<Map<String, SpamLabel>> = spamLabels.labels

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    val moduleStatus: StateFlow<ModuleStatus.Info> = ModuleStatus.state

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    /** 供 UI 显示的一次性消息的字符串资源 id（例如保存被拒绝）。 */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = catalog.load()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ModuleStatus.state.collect { status ->
                rules.attachRemote(ModuleStatus.remotePrefs())
                // 内置模型更新后（比如 App 升级），已有标注对应的微调量是针对旧模型学的：
                // 模块服务连上、远程配置就绪后自动重新拟合一次。必须在 attachRemote 之后，否则版本号写不进去。
                if (status.active) retuneIfModelChanged()
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            spamLabels.labels.drop(1).collectLatest { retune(it.values) }
        }
        // 每天最多静默检查一次更新
        if (System.currentTimeMillis() - updates.lastCheckedAt > 24 * 60 * 60 * 1000L) checkForUpdate(silent = true)
    }

    /** 检查 GitHub Releases；[silent] 为启动时的静默检查，只在有新版本时改变状态。 */
    fun checkForUpdate(silent: Boolean = false) {
        val state = _updateState.value
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        if (!silent) _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = updates.fetchLatest()
                updates.lastCheckedAt = System.currentTimeMillis()
                if (AppVersion.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                    _updateState.value = UpdateState.Available(info)
                } else if (!silent) {
                    _updateState.value = UpdateState.UpToDate(info.tag)
                }
            } catch (t: Throwable) {
                if (!silent) _updateState.value = UpdateState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun downloadUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        _updateState.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = updates.download(info) { p -> _updateState.value = UpdateState.Downloading(info, p) }
                _updateState.value = UpdateState.Ready(info, file)
            } catch (t: Throwable) {
                _updateState.value = UpdateState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** 拉起系统安装器；返回是否成功启动。 */
    fun installUpdate(context: Context): Boolean {
        val ready = _updateState.value as? UpdateState.Ready ?: return false
        return runCatching { context.startActivity(updates.installIntent(ready.file)); true }.getOrDefault(false)
    }

    fun dismissUpdate() {
        val s = _updateState.value
        if (s is UpdateState.UpToDate || s is UpdateState.Error) _updateState.value = UpdateState.Idle
    }

    private fun retuneIfModelChanged() {
        val labels = spamLabels.labels.value.values
        val base = SpamModel.bundled() ?: return
        if (labels.isNotEmpty() && spamLabels.tunedModelFingerprint != base.fingerprint) retune(labels)
    }

    /** 根据全部标注重新拟合调优增量，并下发给 system_server。 */
    /** 解释用的模型：内置模型叠加当前微调量；微调后刷新。 */
    @Volatile private var explainModel: SpamModel? = null

    /** 计算一条记录的骚扰分数依据；模型不可用时返回 null。 */
    fun explain(record: NotificationRecord): SpamExplainer.Explanation? {
        val model = explainModel ?: run {
            val base = SpamModel.bundled() ?: return null
            (SpamDeltaWriter.read()?.let { base.withDelta(it) } ?: base).also { explainModel = it }
        }
        return SpamExplainer.explain(model, SpamLabelRepository.trainingText(record))
    }

    private fun retune(labels: Collection<SpamLabel>) {
        val base = SpamModel.bundled() ?: return
        val delta = SpamTuner.fit(base, labels.map { SpamTuner.Sample(it.text, it.spam) })
        explainModel = base.withDelta(delta)
        if (SpamDeltaWriter.write(delta)) {
            // 版本号写入远程配置成功才算完成；失败（如模块服务尚未就绪）则下次再试
            if (report(rules.setSpamDeltaVersion(System.currentTimeMillis()))) {
                spamLabels.tunedModelFingerprint = base.fingerprint
            }
        } else {
            _messages.tryEmit(R.string.save_need_active)
        }
    }

    /** 传入 null 表示清除标注。 */
    fun setLabel(record: NotificationRecord, spam: Boolean?) {
        if (spam == null) spamLabels.remove(record.id) else spamLabels.set(record, spam)
    }

    fun clearLabels() = spamLabels.clear()

    /** 用当前模型（内置 + 微调）重新计算一条记录的骚扰分数，只返回结果、不写回记录。 */
    fun rescore(record: NotificationRecord): SpamJudge.Verdict? {
        val model = explainModel ?: run {
            val base = SpamModel.bundled() ?: return null
            (SpamDeltaWriter.read()?.let { base.withDelta(it) } ?: base).also { explainModel = it }
        }
        return SpamJudge.judge(model, config.value.spamThreshold, SpamLabelRepository.trainingText(record))
    }

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

    fun reorderRules(orderedIds: List<String>) {
        report(rules.reorder(orderedIds))
    }

    fun toggleRule(id: String, enabled: Boolean) {
        report(rules.toggleRule(id, enabled))
    }

    private fun report(saved: Boolean): Boolean {
        if (!saved) _messages.tryEmit(R.string.save_need_active)
        return saved
    }

    fun clearLogs() = logs.clear()

    fun clearDebugLog() = debugLog.clear()

    fun setDebugLogEnabled(enabled: Boolean) {
        report(rules.setDebugLogEnabled(enabled))
    }

    fun setJudgeLogEnabled(enabled: Boolean) {
        report(rules.setJudgeLogEnabled(enabled))
    }

    fun labelFor(packageName: String): String {
        val cached = _apps.value.firstOrNull { it.packageName == packageName }
        if (cached != null) return cached.label
        return catalog.labelFor(packageName)
    }

    fun sendTest(keyword: String) {
        TestNotifier.send(getApplication(), keyword)
    }
}
