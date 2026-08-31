package moe.notice.filter.ui.logs

import android.app.Notification
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import moe.notice.filter.R
import moe.notice.filter.ui.theme.groupedListShape
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.ui.rules.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    records: List<NotificationRecord>,
    query: String,
    filter: Int,
    appPackage: String?,
    appLabel: (String) -> String,
    contentPadding: PaddingValues,
) {
    var selected by remember { mutableStateOf<NotificationRecord?>(null) }
    val visible = remember(records, filter, query, appPackage) {
        val q = query.trim()
        records.filter { item ->
            val pass = when (filter) {
                1 -> item.blocked
                2 -> !item.blocked
                else -> true
            }
            if (!pass) return@filter false
            if (appPackage != null && item.packageName != appPackage) return@filter false
            if (q.isEmpty()) return@filter true
            item.title.contains(q, ignoreCase = true) ||
                item.text.contains(q, ignoreCase = true) ||
                item.packageName.contains(q, ignoreCase = true) ||
                appLabel(item.packageName).contains(q, ignoreCase = true) ||
                (item.ruleName?.contains(q, ignoreCase = true) == true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (query.isNotBlank() || records.isNotEmpty()) {
                            R.string.empty_logs_search
                        } else {
                            R.string.empty_logs_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (query.isBlank() && records.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_logs_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                    LogCard(
                        item = item,
                        shape = groupedListShape(index, visible.size),
                        appLabel = appLabel(item.packageName),
                        onClick = { selected = item },
                    )
                }
            }
        }
    }

    selected?.let { record ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            NotificationDetailSheet(
                record = record,
                appLabel = appLabel(record.packageName),
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogCard(
    item: NotificationRecord,
    shape: Shape,
    appLabel: String,
    onClick: () -> Unit,
) {
    val title = item.title.ifBlank { appLabel }
    val meta = buildString {
        if (title != appLabel) {
            append(appLabel)
            append(" · ")
        }
        append(formatTime(item.timestamp))
        if (item.blocked && !item.ruleName.isNullOrBlank()) {
            append(" · ")
            append(item.ruleName)
        }
    }
    val statusColor = if (item.blocked) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(item.packageName, Modifier.size(32.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = stringResource(
                    if (item.blocked) R.string.badge_blocked else R.string.badge_allowed,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun NotificationDetailSheet(
    record: NotificationRecord,
    appLabel: String,
) {
    val details = record.details
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.log_detail_title), style = MaterialTheme.typography.titleLarge)
        DetailRow(stringResource(R.string.log_detail_app), appLabel)
        DetailRow(stringResource(R.string.log_detail_package), record.packageName)
        DetailRow(
            stringResource(R.string.log_detail_status),
            stringResource(if (record.blocked) R.string.badge_blocked else R.string.badge_allowed),
        )
        if (record.blocked && !record.ruleName.isNullOrBlank()) {
            DetailRow(stringResource(R.string.log_detail_rule), record.ruleName.orEmpty())
        }
        DetailRow(
            stringResource(R.string.log_detail_time),
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(record.timestamp)),
        )
        DetailRow(stringResource(R.string.log_detail_title_field), record.title)
        DetailRow(stringResource(R.string.log_detail_text), record.text)
        DetailRow(stringResource(R.string.log_detail_big_title), details.bigTitle)
        DetailRow(stringResource(R.string.log_detail_big_text), details.bigText)
        DetailRow(stringResource(R.string.log_detail_sub_text), details.subText)
        DetailRow(stringResource(R.string.log_detail_info_text), details.infoText)
        DetailRow(stringResource(R.string.log_detail_summary), details.summaryText)
        DetailRow(stringResource(R.string.log_detail_ticker), details.ticker)
        DetailRow(stringResource(R.string.log_detail_channel), details.channelId)
        DetailRow(stringResource(R.string.log_detail_category), details.category)
        DetailRow(stringResource(R.string.log_detail_group), details.groupKey)
        DetailRow(stringResource(R.string.log_detail_template), details.template)
        if (details.notificationId != 0) {
            DetailRow(stringResource(R.string.log_detail_id), details.notificationId.toString())
        }
        DetailRow(stringResource(R.string.log_detail_tag), details.tag)
        if (details.number != 0) {
            DetailRow(stringResource(R.string.log_detail_number), details.number.toString())
        }
        DetailRow(stringResource(R.string.log_detail_progress), details.progress)
        DetailRow(stringResource(R.string.log_detail_visibility), visibilityLabel(details.visibility))
        DetailRow(stringResource(R.string.log_detail_flags), flagLabels(details.flags).joinToString("、"))
        DetailRow(stringResource(R.string.log_detail_actions), details.actions.joinToString("、"))
        DetailRow(stringResource(R.string.log_detail_lines), details.textLines.joinToString("\n"))
        DetailRow(stringResource(R.string.log_detail_messages), details.messages.joinToString("\n"))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun visibilityLabel(visibility: Int): String = when (visibility) {
    Notification.VISIBILITY_PUBLIC -> "公开"
    Notification.VISIBILITY_PRIVATE -> "锁屏隐藏内容"
    Notification.VISIBILITY_SECRET -> "锁屏不显示"
    else -> ""
}

private fun flagLabels(flags: Int): List<String> {
    if (flags == 0) return emptyList()
    val out = ArrayList<String>()
    if (flags and Notification.FLAG_ONGOING_EVENT != 0) out += "持续"
    if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) out += "前台服务"
    if (flags and Notification.FLAG_AUTO_CANCEL != 0) out += "点按后取消"
    if (flags and Notification.FLAG_NO_CLEAR != 0) out += "不可清除"
    if (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) out += "仅提醒一次"
    if (flags and Notification.FLAG_INSISTENT != 0) out += "持续响铃"
    if (flags and Notification.FLAG_LOCAL_ONLY != 0) out += "仅本机"
    if (flags and Notification.FLAG_GROUP_SUMMARY != 0) out += "分组摘要"
    if (flags and Notification.FLAG_BUBBLE != 0) out += "气泡"
    return out
}

private fun formatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }
}
