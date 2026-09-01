package moe.notice.filter.data

import android.content.SharedPreferences
import moe.notice.filter.FilterPrefs
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.MatchMode
import moe.notice.filter.domain.RuleAction
import org.json.JSONArray
import org.json.JSONObject

object FilterConfigCodec {
    fun encode(config: FilterConfig): String {
        val root = JSONObject()
        root.put("enabled", config.enabled)
        root.put("logEnabled", config.logEnabled)
        root.put("spamEnabled", config.spamEnabled)
        root.put("spamThreshold", config.spamThreshold.toDouble())
        root.put("spamExcludedPackages", JSONArray(config.spamExcludedPackages))
        root.put("spamDeltaVersion", config.spamDeltaVersion)
        root.put("debugLogEnabled", config.debugLogEnabled)
        root.put("judgeLogEnabled", config.judgeLogEnabled)
        val rules = JSONArray()
        for (rule in config.rules) {
            rules.put(encodeRule(rule))
        }
        root.put("rules", rules)
        return root.toString()
    }

    fun decode(json: String): FilterConfig {
        val root = JSONObject(json)
        val array = root.optJSONArray("rules") ?: JSONArray()
        val rules = ArrayList<BlockRule>(array.length())
        for (i in 0 until array.length()) {
            rules += decodeRule(array.getJSONObject(i)).withCompiledRegex()
        }
        return FilterConfig(
            enabled = root.optBoolean("enabled", false),
            logEnabled = root.optBoolean("logEnabled", true),
            rules = rules,
            spamEnabled = root.optBoolean("spamEnabled", false),
            spamThreshold = root.optDouble("spamThreshold", FilterConfig.DEFAULT_SPAM_THRESHOLD.toDouble())
                .toFloat()
                .coerceIn(FilterConfig.MIN_SPAM_THRESHOLD, FilterConfig.MAX_SPAM_THRESHOLD),
            spamExcludedPackages = stringList(root.optJSONArray("spamExcludedPackages")),
            spamDeltaVersion = root.optLong("spamDeltaVersion", 0L),
            debugLogEnabled = root.optBoolean("debugLogEnabled", true),
            judgeLogEnabled = root.optBoolean("judgeLogEnabled", false),
        )
    }

    fun fromPrefs(prefs: SharedPreferences): FilterConfig {
        val json = prefs.getString(FilterPrefs.KEY_CONFIG, null)
        if (!json.isNullOrBlank()) {
            return runCatching { decode(json) }.getOrDefault(FilterConfig())
        }
        return fromLegacy(prefs)
    }

    private fun fromLegacy(prefs: SharedPreferences): FilterConfig {
        val keywords = prefs.getString(FilterPrefs.KEY_KEYWORDS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        val enabled = prefs.getBoolean(FilterPrefs.KEY_ENABLED, false)
        if (keywords.isEmpty()) {
            return FilterConfig(enabled = enabled)
        }
        val regex = prefs.getBoolean(FilterPrefs.KEY_REGEX, false)
        val rule = BlockRule(
            id = "legacy",
            name = "默认规则",
            enabled = true,
            mode = if (regex) MatchMode.REGEX else MatchMode.CONTAINS_ANY,
            keywords = keywords,
        ).withCompiledRegex()
        return FilterConfig(enabled = enabled, rules = listOf(rule))
    }

    private fun encodeRule(rule: BlockRule): JSONObject {
        val obj = JSONObject()
        obj.put("id", rule.id)
        obj.put("name", rule.name)
        obj.put("enabled", rule.enabled)
        obj.put("mode", rule.mode.id)
        obj.put("keywords", JSONArray(rule.keywords))
        obj.put("excludeKeywords", JSONArray(rule.excludeKeywords))
        obj.put("packages", JSONArray(rule.packages))
        obj.put("appListMode", rule.appListMode.id)
        obj.put("action", rule.action.id)
        obj.put("notify", rule.notify)
        return obj
    }

    private fun decodeRule(obj: JSONObject): BlockRule = BlockRule(
        id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        name = obj.optString("name"),
        enabled = obj.optBoolean("enabled", true),
        mode = MatchMode.fromId(obj.optString("mode")),
        keywords = stringList(obj.optJSONArray("keywords")),
        excludeKeywords = stringList(obj.optJSONArray("excludeKeywords")),
        packages = stringList(obj.optJSONArray("packages")),
        appListMode = AppListMode.fromId(obj.optString("appListMode")),
        action = RuleAction.fromId(obj.optString("action")),
        notify = obj.optBoolean("notify", true),
    )

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) out += value
        }
        return out
    }
}
