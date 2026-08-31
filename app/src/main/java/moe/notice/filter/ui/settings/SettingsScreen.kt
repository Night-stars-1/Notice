package moe.notice.filter.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
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
    spamExcludedCount: Int,
    onPickSpamApps: () -> Unit,
    tuneSampleCount: Int,
    onClearLabels: () -> Unit,
    onRequestTest: () -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmClearLabels by remember { mutableStateOf(false) }
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
        item { SectionHeader(stringResource(R.string.section_debug)) }
        item {
            SettingRow(
                icon = Icons.Outlined.Science,
                title = stringResource(R.string.test),
                supporting = stringResource(R.string.test_hint),
                shape = groupedListShape(0, 1),
                onClick = onRequestTest,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
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
                shape = groupedListShape(0, 2),
            )
        }
        item {
            val context = LocalContext.current
            val url = stringResource(R.string.about_github_url)
            SettingRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.about_github),
                supporting = stringResource(R.string.about_github_hint),
                shape = groupedListShape(1, 2),
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
