package moe.notice.filter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.notice.filter.FilterPrefs
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig

/**
 * 规则保存在 Xposed 框架的远程偏好设置中（system_server 唯一能读取的存储）。
 * 另外保留一份私有的本地副本，仅用于在模块未激活时让界面仍能显示最近已知的规则；
 * 除非已绑定框架服务，否则不会写入任何内容。
 */
class RuleRepository(context: Context) {
    private val cache: SharedPreferences =
        context.applicationContext.getSharedPreferences(FilterPrefs.NAME, Context.MODE_PRIVATE)

    @Volatile
    private var remote: SharedPreferences? = null

    private val _config = MutableStateFlow(FilterConfigCodec.fromPrefs(cache))
    val config: StateFlow<FilterConfig> = _config.asStateFlow()

    /** 在 Xposed 服务连接（prefs）或断开（null）时调用。 */
    fun attachRemote(prefs: SharedPreferences?) {
        remote = prefs
        if (prefs == null) return
        if (!prefs.contains(FilterPrefs.KEY_CONFIG)) {
            // 新 API 上的首次运行：把服务接入前的本地规则一次性迁移过去。
            val local = _config.value
            if (local.rules.isNotEmpty() || local.enabled) write(prefs, local)
            return
        }
        val current = FilterConfigCodec.fromPrefs(prefs)
        cacheLocally(current)
        _config.value = current
    }

    /** 模块未激活时返回 false（且不写入任何内容）。 */
    fun save(config: FilterConfig): Boolean {
        val prefs = remote ?: return false
        write(prefs, config)
        return true
    }

    fun setEnabled(enabled: Boolean): Boolean = save(_config.value.copy(enabled = enabled))

    fun setLogEnabled(logEnabled: Boolean): Boolean = save(_config.value.copy(logEnabled = logEnabled))

    fun setSpamEnabled(spamEnabled: Boolean): Boolean = save(_config.value.copy(spamEnabled = spamEnabled))

    fun setSpamThreshold(threshold: Float): Boolean = save(
        _config.value.copy(
            spamThreshold = threshold.coerceIn(FilterConfig.MIN_SPAM_THRESHOLD, FilterConfig.MAX_SPAM_THRESHOLD),
        ),
    )

    fun setSpamExcludedPackages(packages: List<String>): Boolean =
        save(_config.value.copy(spamExcludedPackages = packages.distinct().sorted()))

    fun setSpamDeltaVersion(version: Long): Boolean = save(_config.value.copy(spamDeltaVersion = version))

    fun setDebugLogEnabled(enabled: Boolean): Boolean = save(_config.value.copy(debugLogEnabled = enabled))

    fun upsert(rule: BlockRule): Boolean {
        val current = _config.value
        val rules = current.rules.toMutableList()
        val stored = rule.withCompiledRegex()
        val index = rules.indexOfFirst { it.id == stored.id }
        if (index >= 0) rules[index] = stored else rules += stored
        return save(current.copy(rules = rules))
    }

    fun delete(ruleId: String): Boolean {
        val current = _config.value
        return save(current.copy(rules = current.rules.filterNot { it.id == ruleId }))
    }

    fun toggleRule(ruleId: String, enabled: Boolean): Boolean {
        val current = _config.value
        return save(
            current.copy(
                rules = current.rules.map { if (it.id == ruleId) it.copy(enabled = enabled) else it },
            ),
        )
    }

    private fun write(prefs: SharedPreferences, config: FilterConfig) {
        prefs.edit()
            .putString(FilterPrefs.KEY_CONFIG, FilterConfigCodec.encode(config))
            .putBoolean(FilterPrefs.KEY_ENABLED, config.enabled)
            .commit()
        cacheLocally(config)
        _config.value = config
    }

    private fun cacheLocally(config: FilterConfig) {
        cache.edit()
            .putString(FilterPrefs.KEY_CONFIG, FilterConfigCodec.encode(config))
            .remove(FilterPrefs.KEY_KEYWORDS)
            .remove(FilterPrefs.KEY_REGEX)
            .putBoolean(FilterPrefs.KEY_ENABLED, config.enabled)
            .apply()
    }
}
