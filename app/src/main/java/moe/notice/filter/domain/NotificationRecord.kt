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
    /** 同一条通知后续有多少次更新被合并进了这条记录。 */
    val updateCount: Int = 0,
)
