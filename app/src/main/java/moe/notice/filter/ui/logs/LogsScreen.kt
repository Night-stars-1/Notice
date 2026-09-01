package moe.notice.filter.ui.logs

import android.app.Notification
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import moe.notice.filter.domain.SpamExplainer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import moe.notice.filter.R
import moe.notice.filter.ui.theme.groupedListShape
import moe.notice.filter.data.orSystemPackage
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.domain.SpamJudge
import moe.notice.filter.ui.rules.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    records: List<NotificationRecord>,
    query: String,
    filter: Int,
    appPackages: Set<String>,
    appLabel: (String) -> String,
    labels: Map<String, Boolean>,
    onLabel: (NotificationRecord, Boolean?) -> Unit,
    explain: (NotificationRecord) -> SpamExplainer.Explanation?,
    onRescore: (NotificationRecord) -> Unit,
    contentPadding: PaddingValues,
) {
    var selected by remember { mutableStateOf<NotificationRecord?>(null) }
    val visible = remember(records, filter, query, appPackages) {
        val q = query.trim()
        records.filter { item ->
            val pass = when (filter) {
                1 -> item.blocked
                2 -> !item.blocked
                3 -> item.blocked && item.ruleId == SpamJudge.RULE_ID
                else -> true
            }
            if (!pass) return@filter false
            if (appPackages.isNotEmpty() && item.packageName.orSystemPackage() !in appPackages) return@filter false
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
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // 向下滚动几项之后才显示，且仅在用户向上回滚时显示。
            var scrollingUp by remember { mutableStateOf(false) }
            val nestedScroll = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y > 0.5f) scrollingUp = true else if (available.y < -0.5f) scrollingUp = false
                        return Offset.Zero
                    }
                }
            }
            val showTop by remember {
                derivedStateOf { scrollingUp && listState.firstVisibleItemIndex >= 3 }
            }
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScroll),
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
            androidx.compose.animation.AnimatedVisibility(
                visible = showTop,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.VerticalAlignTop,
                        contentDescription = stringResource(R.string.scroll_to_top),
                    )
                }
            }
            }
        }
    }

    selected?.let { chosen ->
        // 重新评分后记录会更新：始终显示列表里的最新版本
        val record = records.firstOrNull { it.id == chosen.id } ?: chosen
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            NotificationDetailSheet(
                record = record,
                appLabel = appLabel(record.packageName),
                label = labels[record.id],
                onLabel = { onLabel(record, it) },
                explanation = if (record.details.spamScore != null) {
                    remember(record.id, record.details.spamScore) { explain(record) }
                } else {
                    null
                },
                onRescore = { onRescore(record) },
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
        if (!item.ruleName.isNullOrBlank()) {
            append(" · ")
            append(item.ruleName)
        }
        if (item.updateCount > 0) {
            append(" · ")
            append(stringResource(R.string.log_updated_times, item.updateCount))
        }
    }
    val badgeContainer = if (item.blocked) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeContent = if (item.blocked) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(item.packageName, Modifier.size(40.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    color = badgeContainer,
                    contentColor = badgeContent,
                ) {
                    Text(
                        text = stringResource(item.badgeTextRes()),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        textAlign = TextAlign.Center,
                    )
                }
                item.details.spamScore?.let { score ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = formatScore(score),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailSheet(
    record: NotificationRecord,
    appLabel: String,
    label: Boolean?,
    onLabel: (Boolean?) -> Unit,
    explanation: SpamExplainer.Explanation?,
    onRescore: () -> Unit,
) {
    val details = record.details
    // 解释是针对「标题\n正文」算的：把片段区间拆回标题 / 正文各自的下标。
    val titleLen = if (record.title.isNotBlank()) record.title.length else -1
    val positiveRanges = explanation?.positives.orEmpty().map { it.range }
    val titleHighlights = positiveRanges.filter { titleLen >= 0 && it.last < titleLen }
    val textHighlights = positiveRanges.mapNotNull { r ->
        val offset = if (titleLen >= 0) titleLen + 1 else 0
        if (r.first >= offset) (r.first - offset)..(r.last - offset) else null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(record.packageName, Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title.ifBlank { appLabel },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (record.blocked) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (record.blocked) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Text(
                    text = stringResource(record.badgeTextRes()),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
        LabelCard(label = label, onLabel = onLabel)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailRow(stringResource(R.string.log_detail_app), appLabel)
                DetailRow(stringResource(R.string.log_detail_package), record.packageName.orSystemPackage())
                DetailRow(
                    stringResource(R.string.log_detail_status),
                    stringResource(if (record.blocked) R.string.badge_blocked else R.string.badge_allowed),
                )
                if (!record.ruleName.isNullOrBlank()) {
                    DetailRow(stringResource(R.string.log_detail_rule), record.ruleName.orEmpty())
                }
                details.spamScore?.let { score ->
                    val shown = formatScore(score)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DetailRow(
                                stringResource(R.string.log_detail_spam_score),
                                if (details.spamProtected) stringResource(R.string.log_spam_protected, shown) else shown,
                            )
                        }
                        IconButton(onClick = onRescore) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.log_rescore),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (explanation != null && (explanation.positives.isNotEmpty() || explanation.negatives.isNotEmpty())) {
                    ReasonRow(explanation)
                }
                DetailRow(
                    stringResource(R.string.log_detail_time),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(record.timestamp)),
                )
                if (record.updateCount > 0) {
                    DetailRow(stringResource(R.string.log_detail_updates), record.updateCount.toString())
                }
                DetailRow(stringResource(R.string.log_detail_title_field), record.title, titleHighlights)
                DetailRow(stringResource(R.string.log_detail_text), record.text, textHighlights)
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
    }
}

@Composable
private fun DetailRow(label: String, value: String, highlights: List<IntRange> = emptyList()) {
    if (value.isBlank()) return
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.errorContainer,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
    val annotated = remember(value, highlights, highlightStyle) {
        buildAnnotatedString {
            append(value)
            for (r in highlights) {
                val start = r.first.coerceIn(0, value.length)
                val end = (r.last + 1).coerceIn(start, value.length)
                if (end > start) addStyle(highlightStyle, start, end)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
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

private fun formatScore(score: Float): String = String.format(Locale.ROOT, "%.2f", score)

/** 徽标文字：模型拦截显示 "AI"，否则显示已拦截/已放行。 */
private fun NotificationRecord.badgeTextRes(): Int = when {
    !blocked -> R.string.badge_allowed
    ruleId == SpamJudge.RULE_ID -> R.string.badge_ai
    else -> R.string.badge_blocked
}

/**
 * 垃圾/正常标注，以 M3 connected button group 形式放在与详情卡片风格一致的卡片中。
 * 来自 M3 的规范：connected group 横跨整个 surface，按钮间距 2dp，首尾为 connected 形状，
 * 选中的 toggle = filled + 方形，未选中 = tonal + 圆形，选中时 outlined 图标 → filled 图标。
 * 我的决定：组上方的说明文字以及 12dp 的卡片内边距。
 */
@Composable
private fun LabelCard(label: Boolean?, onLabel: (Boolean?) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.label_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = label == true,
                    onCheckedChange = { onLabel(if (it) true else null) },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Icon(
                        imageVector = if (label == true) Icons.Filled.Report else Icons.Outlined.Report,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.label_spam))
                }
                ToggleButton(
                    checked = label == false,
                    onCheckedChange = { onLabel(if (it) false else null) },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Icon(
                        imageVector = if (label == false) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.label_ham))
                }
            }
        }
    }
}

/** 骚扰分数的依据：推高分数的片段（红）与拉低分数的片段（灰），数值为 logit 贡献。 */
@Composable
private fun ReasonRow(explanation: SpamExplainer.Explanation) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.log_detail_reasons),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            explanation.positives.forEach { term ->
                ReasonChip(
                    text = term.text + " +" + String.format(Locale.ROOT, "%.1f", term.contribution),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            explanation.negatives.forEach { term ->
                ReasonChip(
                    text = term.text + " −" + String.format(Locale.ROOT, "%.1f", -term.contribution),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReasonChip(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
