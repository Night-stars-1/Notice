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
    /** Spam probability from [SpamModel] when the model was enabled at judge time; null otherwise. */
    val spamScore: Float? = null,
    /** True when the message matched a never-block marker (verification code) and was let through. */
    val spamProtected: Boolean = false,
)
