package moe.notice.filter.domain

/** 版本号比较：支持 `v1.2.3`、`1.2.3-dev`、`1.2.3-rc1` 等写法，只比较数字部分。 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val n = maxOf(parts.size, other.parts.size)
        for (i in 0 until n) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        /** 解析失败返回 null（例如 tag 不是版本号）。 */
        fun parse(raw: String): AppVersion? {
            val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            if (core.isEmpty()) return null
            val parts = core.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts)
        }

        /** [latestTag] 是否比当前 [currentName] 新。任一无法解析时视为没有更新。 */
        fun isNewer(latestTag: String, currentName: String): Boolean {
            val latest = parse(latestTag) ?: return false
            val current = parse(currentName) ?: return false
            return latest > current
        }
    }
}
