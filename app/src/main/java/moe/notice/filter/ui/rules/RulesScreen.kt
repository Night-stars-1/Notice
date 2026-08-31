package moe.notice.filter.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.notice.filter.BuildConfig
import moe.notice.filter.R
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.MatchMode
import moe.notice.filter.domain.RuleAction
import moe.notice.filter.ui.components.SectionHeader
import moe.notice.filter.ui.theme.groupedListShape

@Composable
fun RulesScreen(
    config: FilterConfig,
    moduleActive: Boolean,
    xposedApi: Int,
    appLabel: (String) -> String,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (BlockRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onReorderRules: (List<String>) -> Unit,
    contentPadding: PaddingValues,
) {
    var pendingDelete by remember { mutableStateOf<BlockRule?>(null) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // 长按拖动排序：拖动期间用本地顺序渲染，松手后一次性写回配置。
    var order by remember { mutableStateOf(config.rules) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(config.rules) { if (draggingId == null) order = config.rules }
    val rules = if (draggingId != null) order else config.rules

    fun onDragBy(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = items.firstOrNull { info ->
            info.key != id && order.any { it.id == info.key } && center >= info.offset && center <= info.offset + info.size
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // 交换后被拖项的布局位置随之移动，补偿偏移让它仍跟着手指。
        dragOffset -= (target.offset - current.offset)
    }

    fun finishDrag() {
        if (draggingId != null) onReorderRules(order.map { it.id })
        draggingId = null
        dragOffset = 0f
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            ModuleStatusCard(
                active = moduleActive,
                xposedApi = xposedApi,
            )
            Spacer(Modifier.height(6.dp))
        }
        item {
            SectionHeader(
                title = stringResource(R.string.section_rules),
                trailing = config.rules.size.takeIf { it > 0 }?.toString(),
            )
        }
        if (config.rules.isEmpty()) {
            item { EmptyRules() }
        } else {
            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                val dragging = rule.id == draggingId
                RuleCard(
                    rule = rule,
                    shape = groupedListShape(index, rules.size),
                    appLabel = appLabel,
                    dragging = dragging,
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(rule.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    order = config.rules
                                    draggingId = rule.id
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDragBy(amount.y)
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() },
                            )
                        },
                    onToggle = { onToggleRule(rule.id, it) },
                    onClick = { onEditRule(rule) },
                    onDelete = { pendingDelete = rule },
                )
            }
        }
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_rule_title)) },
            text = { Text(stringResource(R.string.delete_rule_message, rule.displayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRule(rule.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ModuleStatusCard(
    active: Boolean,
    xposedApi: Int,
) {
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (active) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 32.dp, y = 38.dp)
                    .size(136.dp),
                tint = onContainer.copy(alpha = 0.55f),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 120.dp, bottom = 20.dp),
            ) {
                Text(
                    text = stringResource(
                        if (active) R.string.status_active_title else R.string.status_inactive_title,
                    ),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = onContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.status_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onContainer,
                )
                if (active) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.status_api, xposedApi),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRules() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.FilterAltOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.empty_rules_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_rules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleCard(
    rule: BlockRule,
    shape: Shape,
    appLabel: (String) -> String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    // 长按抬起：轻微放大 + 容器色调提亮 + 浅阴影，全部走 motionScheme 弹簧，避免硬切。
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(if (dragging) 1.02f else 1f, motion.defaultSpatialSpec(), label = "dragScale")
    val elevation by animateDpAsState(if (dragging) 4.dp else 0.dp, motion.defaultEffectsSpec(), label = "dragElevation")
    val container by animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        motion.defaultEffectsSpec(),
        label = "dragColor",
    )
    val names = rule.packages.take(3).joinToString("、", transform = appLabel)
    val apps = when {
        rule.packages.isEmpty() -> stringResource(R.string.apps_all)
        rule.appListMode == AppListMode.BLACKLIST -> stringResource(
            R.string.apps_excluded,
            rule.packages.size,
            names,
        )
        else -> stringResource(
            R.string.apps_selected,
            rule.packages.size,
            names,
        )
    }
    val meta = buildString {
        if (rule.action != RuleAction.BLOCK) append(stringResource(rule.action.labelRes())).append(" · ")
        append(stringResource(rule.mode.labelRes())).append(" · ").append(apps)
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = container,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = rule.displayName(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.preview(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                thumbContent = if (rule.enabled) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

internal fun BlockRule.displayName(): String = name.ifBlank { "未命名规则" }

internal fun AppListMode.labelRes(): Int = when (this) {
    AppListMode.WHITELIST -> R.string.app_list_whitelist
    AppListMode.BLACKLIST -> R.string.app_list_blacklist
}

internal fun RuleAction.labelRes(): Int = when (this) {
    RuleAction.BLOCK -> R.string.action_block
    RuleAction.ALLOW -> R.string.action_allow
    RuleAction.SKIP_AI -> R.string.action_skip_ai
}

internal fun MatchMode.labelRes(): Int = when (this) {
    MatchMode.CONTAINS_ANY -> R.string.mode_contains_any
    MatchMode.CONTAINS_ALL -> R.string.mode_contains_all
    MatchMode.NOT_CONTAINS_ANY -> R.string.mode_not_contains_any
    MatchMode.NOT_CONTAINS_ALL -> R.string.mode_not_contains_all
    MatchMode.CONTAINS_A_NOT_B -> R.string.mode_contains_a_not_b
    MatchMode.REGEX -> R.string.mode_regex
    MatchMode.ALL_CONTENT -> R.string.mode_all_content
}

private fun BlockRule.preview(): String = when (mode) {
    MatchMode.ALL_CONTENT -> "匹配所选应用的全部通知"
    MatchMode.CONTAINS_A_NOT_B -> {
        val a = keywords.joinToString(" / ").ifBlank { "—" }
        val b = excludeKeywords.joinToString(" / ").ifBlank { "—" }
        "包含 $a · 不含 $b"
    }
    MatchMode.REGEX -> keywords.joinToString("\n").ifBlank { "未填写正则" }
    else -> keywords.joinToString(" / ").ifBlank { "未填写关键词" }
}
