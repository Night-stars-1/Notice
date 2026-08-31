package moe.notice.filter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.notice.filter.FilterPrefs
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig

class RuleRepository(context: Context) {
    private val prefs: SharedPreferences = openPrefs(context.applicationContext)

    private val _config = MutableStateFlow(FilterConfigCodec.fromPrefs(prefs))
    val config: StateFlow<FilterConfig> = _config.asStateFlow()

    fun save(config: FilterConfig) {
        prefs.edit()
            .putString(FilterPrefs.KEY_CONFIG, FilterConfigCodec.encode(config))
            .remove(FilterPrefs.KEY_KEYWORDS)
            .remove(FilterPrefs.KEY_REGEX)
            .putBoolean(FilterPrefs.KEY_ENABLED, config.enabled)
            .commit()
        _config.value = config
    }

    fun setEnabled(enabled: Boolean) {
        save(_config.value.copy(enabled = enabled))
    }

    fun setLogEnabled(logEnabled: Boolean) {
        save(_config.value.copy(logEnabled = logEnabled))
    }

    fun upsert(rule: BlockRule) {
        val current = _config.value
        val rules = current.rules.toMutableList()
        val stored = rule.withCompiledRegex()
        val index = rules.indexOfFirst { it.id == stored.id }
        if (index >= 0) rules[index] = stored else rules += stored
        save(current.copy(rules = rules))
    }

    fun delete(ruleId: String) {
        val current = _config.value
        save(current.copy(rules = current.rules.filterNot { it.id == ruleId }))
    }

    fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _config.value
        save(
            current.copy(
                rules = current.rules.map { if (it.id == ruleId) it.copy(enabled = enabled) else it },
            ),
        )
    }

    private companion object {
        fun openPrefs(context: Context): SharedPreferences {
            return try {
                context.getSharedPreferences(FilterPrefs.NAME, Context.MODE_WORLD_READABLE)
            } catch (_: SecurityException) {
                context.getSharedPreferences(FilterPrefs.NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
