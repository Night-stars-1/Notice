package moe.notice.filter.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import moe.notice.filter.R
import moe.notice.filter.data.DebugLine
import moe.notice.filter.data.JudgeLine
import moe.notice.filter.ui.rules.AppIcon
import moe.notice.filter.ui.theme.groupedListShape

/**
 * 模块运行日志。与记录页同款的分组卡片：judge 判定行解析成「应用 + 结果 + 分数 + 文本摘要」，
 * 其它行显示级别图标、时间与消息；点击卡片展开完整内容（规则转储 / 异常堆栈）。
 */
@Composable
fun DebugLogScreen(
    lines: List<DebugLine>,
    appLabel: (String) -> String,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by remember { mutableIntStateOf(0) } // 0 全部 / 1 仅错误 / 2 不含判定
    var filterMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exported = stringResource(R.string.debug_log_exported)
    val exportFailed = stringResource(R.string.debug_log_export_failed)
    val visible = remember(lines, filter) {
        when (filter) {
            1 -> lines.filter { it.isError }
            2 -> lines.filterNot { it.isJudge }
            else -> lines
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val filterLabels = listOf(R.string.filter_all, R.string.debug_filter_errors, R.string.debug_filter_no_judge)
    // 通过系统文件选择器保存为文本文件，不需要 FileProvider 和存储权限。
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = visible.asReversed().joinToString("\n") { plainText(it, timeFormat) }
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
        scope.launch { snackbar.showSnackbar(if (ok) exported else exportFailed) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_log)) },
                subtitle = {
                    Text(
                        stringResource(filterLabels[filter]) + " · " +
                            stringResource(R.string.debug_log_count, visible.size),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filter_all))
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            filterLabels.forEachIndexed { value, label ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        filter = value
                                        filterMenu = false
                                    },
                                    trailingIcon = {
                                        if (filter == value) Icon(Icons.Outlined.Check, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                            exporter.launch("notice-log-$stamp.txt")
                        },
                        enabled = visible.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.debug_log_export))
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = lines.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.debug_log_clear))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.debug_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(visible, key = { _, line -> line.key }) { index, line ->
                val open = line.key in expanded
                LogEntryCard(
                    line = line,
                    time = timeFormat.format(Date(line.timestamp)),
                    appLabel = appLabel,
                    expanded = open,
                    shape = groupedListShape(index, visible.size),
                    onClick = { expanded = if (open) expanded - line.key else expanded + line.key },
                )
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.debug_log)) },
            text = { Text(stringResource(R.string.debug_log_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text(stringResource(R.string.debug_log_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LogEntryCard(
    line: DebugLine,
    time: String,
    appLabel: (String) -> String,
    expanded: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val judge = remember(line.message) { JudgeLine.parse(line.message) }
    val container = if (line.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (line.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (judge != null) {
                JudgeHeader(judge, time, appLabel)
            } else {
                PlainHeader(line, time)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                CodeBlock(
                    text = buildString {
                        append(line.message)
                        if (line.trace.isNotBlank()) {
                            append('\n')
                            append(line.trace)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun JudgeHeader(judge: JudgeLine, time: String, appLabel: (String) -> String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(judge.pkg, Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = appLabel(judge.pkg),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(time)
                    judge.ruleName?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val blocked = judge.blocked
            Surface(
                shape = CircleShape,
                color = if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = stringResource(if (blocked) R.string.badge_blocked else R.string.badge_allowed),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
            judge.score?.let { score ->
                Text(
                    text = String.format(Locale.ROOT, "%.2f", score) + if (judge.protected) " 🛡" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (judge.text.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = judge.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlainHeader(line: DebugLine, time: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (line.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Terminal,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (line.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = line.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private val DebugLine.key: Long get() = timestamp * 31 + message.hashCode()

private fun plainText(line: DebugLine, timeFormat: SimpleDateFormat): String {
    val head = timeFormat.format(Date(line.timestamp)) + " " + (if (line.isError) "E" else "I") + " " + line.message
    return if (line.trace.isBlank()) head else head + "\n" + line.trace
}
