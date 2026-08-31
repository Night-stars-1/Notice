package moe.notice.filter.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.notice.filter.BuildConfig
import moe.notice.filter.R
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.ui.components.SectionHeader
import moe.notice.filter.ui.components.SettingRow
import moe.notice.filter.ui.components.SettingSwitchRow
import moe.notice.filter.ui.theme.groupedListShape

@Composable
fun SettingsScreen(
    config: FilterConfig,
    onEnabledChange: (Boolean) -> Unit,
    onLogEnabledChange: (Boolean) -> Unit,
    onRequestTest: () -> Unit,
    contentPadding: PaddingValues,
) {
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
            SettingRow(
                icon = Icons.Outlined.Extension,
                title = stringResource(R.string.about_scope),
                supporting = stringResource(R.string.hint),
                shape = groupedListShape(1, 2),
            )
        }
    }
}
