package moe.notice.filter

object FilterPrefs {
    const val NAME = "filter"
    const val KEY_CONFIG = "config"
    const val KEY_ENABLED = "enabled"
    const val KEY_KEYWORDS = "keywords"
    const val KEY_REGEX = "regex"

    /** 应用保存配置后发出；system_server 收到后重新拉取远程偏好（远程偏好的变更监听在部分框架上不回调）。 */
    const val ACTION_RELOAD = "moe.notice.filter.RELOAD_CONFIG"
}
