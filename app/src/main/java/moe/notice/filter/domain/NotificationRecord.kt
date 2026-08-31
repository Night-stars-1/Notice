package moe.notice.filter.domain

data class NotificationRecord(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val blocked: Boolean,
    val ruleId: String?,
    val ruleName: String?,
    val details: NotificationDetails = NotificationDetails(),
    /** How many later updates of the same notification were merged into this record. */
    val updateCount: Int = 0,
)
