package moe.notice.filter.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import moe.notice.filter.domain.Appearance
import moe.notice.filter.domain.DarkMode
import moe.notice.filter.ui.theme.ThemePresets
import moe.notice.filter.ui.theme.supportsDynamicColor
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import moe.notice.filter.domain.UpdateState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.notice.filter.BuildConfig
import moe.notice.filter.R
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.ui.components.SectionHeader
import moe.notice.filter.ui.components.SettingRow
import moe.notice.filter.ui.components.SettingSliderRow
import moe.notice.filter.ui.components.SettingSwitchRow
import moe.notice.filter.ui.theme.groupedListShape

@Composable
fun SettingsScreen(
    config: FilterConfig,
    onEnabledChange: (Boolean) -> Unit,
    onLogEnabledChange: (Boolean) -> Unit,
    onSpamEnabledChange: (Boolean) -> Unit,
    onSpamThresholdChange: (Float) -> Unit,
    onOpenDebugLog: () -> Unit,
    onDebugLogEnabledChange: (Boolean) -> Unit,
    onJudgeLogEnabledChange: (Boolean) -> Unit,
    appearance: Appearance,
    onDarkModeChange: (DarkMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (String) -> Unit,
    spamExcludedCount: Int,
    onPickSpamApps: () -> Unit,
    tuneSampleCount: Int,
    onClearLabels: () -> Unit,
    onRequestTest: () -> Unit,
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onDismissUpdate: () -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmClearLabels by remember { mutableStateOf(false) }
    val hasUpdate = updateState is UpdateState.Available || updateState is UpdateState.Downloading || updateState is UpdateState.Ready
    var darkModeDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { SectionHeader(stringResource(R.string.section_general)) }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.FilterAlt,
                title = stringResource(R.string.enable_filter),
                supporting = stringResource(R.string.enable_filter_hint),
                checked = config.enabled,
                shape = groupedListShape(0, 2),
                onCheckedChange = onEnabledChange,
            )
        }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.enable_log),
                supporting = stringResource(R.string.enable_log_hint),
                checked = config.logEnabled,
                shape = groupedListShape(1, 2),
                onCheckedChange = onLogEnabledChange,
            )
        }
        item { SectionHeader(stringResource(R.string.section_ai)) }
        val aiCount = if (config.spamEnabled) 4 else 1
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.enable_spam_model),
                supporting = stringResource(R.string.enable_spam_model_hint),
                checked = config.spamEnabled,
                shape = groupedListShape(0, aiCount),
                onCheckedChange = onSpamEnabledChange,
            )
        }
        if (config.spamEnabled) {
            item {
                SettingSliderRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.spam_threshold),
                    supporting = stringResource(R.string.spam_threshold_hint),
                    value = config.spamThreshold,
                    valueRange = FilterConfig.MIN_SPAM_THRESHOLD..FilterConfig.MAX_SPAM_THRESHOLD,
                    steps = 48,
                    shape = groupedListShape(1, aiCount),
                    onValueCommit = onSpamThresholdChange,
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.AppBlocking,
                    title = stringResource(R.string.spam_excluded_apps),
                    supporting = if (spamExcludedCount == 0) {
                        stringResource(R.string.spam_excluded_apps_none)
                    } else {
                        stringResource(R.string.spam_excluded_apps_count, spamExcludedCount)
                    },
                    shape = groupedListShape(2, aiCount),
                    onClick = onPickSpamApps,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.School,
                    title = stringResource(R.string.spam_tune_samples),
                    supporting = stringResource(R.string.spam_tune_samples_hint, tuneSampleCount),
                    shape = groupedListShape(3, aiCount),
                ) {
                    IconButton(
                        onClick = { confirmClearLabels = true },
                        enabled = tuneSampleCount > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.spam_tune_clear),
                        )
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.section_appearance)) }
        val showThemeColor = !(appearance.dynamicColor && supportsDynamicColor)
        val appearanceCount = 1 + (if (supportsDynamicColor) 1 else 0) + (if (showThemeColor) 1 else 0)
        item {
            SettingRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.dark_mode),
                supporting = stringResource(appearance.darkMode.labelRes()),
                shape = groupedListShape(0, appearanceCount),
                onClick = { darkModeDialog = true },
            )
        }
        if (supportsDynamicColor) {
            item {
                SettingSwitchRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.dynamic_color),
                    supporting = stringResource(R.string.dynamic_color_hint),
                    checked = appearance.dynamicColor,
                    shape = groupedListShape(1, appearanceCount),
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }
        if (showThemeColor) {
            item {
                ThemeColorRow(
                    selected = appearance.themeColor,
                    shape = groupedListShape(appearanceCount - 1, appearanceCount),
                    onSelect = onThemeColorChange,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.section_debug)) }
        item {
            SettingRow(
                icon = Icons.Outlined.Science,
                title = stringResource(R.string.test),
                supporting = stringResource(R.string.test_hint),
                shape = groupedListShape(0, 4),
                onClick = onRequestTest,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.debug_log_switch),
                supporting = stringResource(R.string.debug_log_switch_hint),
                checked = config.debugLogEnabled,
                shape = groupedListShape(1, 4),
                onCheckedChange = onDebugLogEnabledChange,
            )
        }
        item {
            SettingSwitchRow(
                icon = Icons.Outlined.Gavel,
                title = stringResource(R.string.judge_log_switch),
                supporting = stringResource(R.string.judge_log_switch_hint),
                checked = config.judgeLogEnabled,
                shape = groupedListShape(2, 4),
                onCheckedChange = onJudgeLogEnabledChange,
            )
        }
        item {
            SettingRow(
                icon = Icons.Outlined.Terminal,
                title = stringResource(R.string.debug_log),
                supporting = stringResource(R.string.debug_log_hint),
                shape = groupedListShape(3, 4),
                onClick = onOpenDebugLog,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.section_about)) }
        item {
            SettingRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about_version),
                supporting = stringResource(
                    R.string.status_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                shape = groupedListShape(0, 3),
            )
        }
        item {
            SettingRow(
                icon = Icons.Outlined.SystemUpdate,
                title = stringResource(R.string.about_update),
                supporting = when (updateState) {
                    UpdateState.Idle -> stringResource(R.string.update_tap_to_check)
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    is UpdateState.Available -> stringResource(R.string.update_available, updateState.info.tag)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, (updateState.progress * 100).toInt().coerceIn(0, 100))
                    is UpdateState.Ready -> stringResource(R.string.update_ready)
                    is UpdateState.Error -> stringResource(R.string.update_error, updateState.message)
                },
                shape = groupedListShape(1, 3),
                onClick = { if (hasUpdate) onOpenUpdateDialog() else onCheckUpdate() },
            ) {
                if (updateState is UpdateState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        item {
            val context = LocalContext.current
            val url = stringResource(R.string.about_github_url)
            SettingRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.about_github),
                supporting = stringResource(R.string.about_github_hint),
                shape = groupedListShape(2, 3),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (updateState is UpdateState.UpToDate || updateState is UpdateState.Error) {
        // 提示性状态只停留几秒，然后回到「点击检查」
        LaunchedEffect(updateState) {
            kotlinx.coroutines.delay(6_000)
            onDismissUpdate()
        }
    }
    if (darkModeDialog) {
        AlertDialog(
            onDismissRequest = { darkModeDialog = false },
            title = { Text(stringResource(R.string.dark_mode)) },
            text = {
                Column {
                    DarkMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onDarkModeChange(mode)
                                    darkModeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = appearance.darkMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { darkModeDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmClearLabels) {
        AlertDialog(
            onDismissRequest = { confirmClearLabels = false },
            title = { Text(stringResource(R.string.spam_tune_samples)) },
            text = { Text(stringResource(R.string.spam_tune_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearLabels()
                    confirmClearLabels = false
                }) { Text(stringResource(R.string.spam_tune_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLabels = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun DarkMode.labelRes(): Int = when (this) {
    DarkMode.SYSTEM -> R.string.dark_mode_system
    DarkMode.LIGHT -> R.string.dark_mode_light
    DarkMode.DARK -> R.string.dark_mode_dark
}

/** 标题加一排种子色色块；选中的色块带有勾选标记。 */
@Composable
private fun ThemeColorRow(
    selected: String,
    shape: Shape,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Colorize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = stringResource(R.string.theme_color), style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemePresets.all.forEach { preset ->
                    val isSelected = preset.id == selected
                    Surface(
                        onClick = { onSelect(preset.id) },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = preset.seed,
                        contentColor = Color.White,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Check, contentDescription = preset.name)
                            }
                        }
                    }
                }
            }
        }
    }
}
