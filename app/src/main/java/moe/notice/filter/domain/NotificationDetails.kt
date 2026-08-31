package moe.notice.filter.domain

data class NotificationDetails(
    val ticker: String = "",
    val subText: String = "",
    val infoText: String = "",
    val summaryText: String = "",
    val bigText: String = "",
    val bigTitle: String = "",
    val channelId: String = "",
    val category: String = "",
    val groupKey: String = "",
    val template: String = "",
    val tag: String = "",
    val notificationId: Int = 0,
    val number: Int = 0,
    val flags: Int = 0,
    val visibility: Int = 0,
    val progress: String = "",
    val actions: List<String> = emptyList(),
    val textLines: List<String> = emptyList(),
    val messages: List<String> = emptyList(),
    /** 判定时模型已启用则为 [SpamModel] 给出的垃圾概率；否则为 null。 */
    val spamScore: Float? = null,
    /** 消息命中了绝不拦截的标记（验证码）并被放行时为 true。 */
    val spamProtected: Boolean = false,
)
