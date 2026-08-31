package moe.notice.filter.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.notice.filter.R
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.MatchMode
import moe.notice.filter.ui.components.KeywordEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    initial: BlockRule,
    isNew: Boolean,
    appLabel: (String) -> String,
    onSave: (BlockRule) -> Unit,
    onPickApps: (BlockRule) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var mode by remember { mutableStateOf(initial.mode) }
    var keywords by remember { mutableStateOf(initial.keywords) }
    var excludeKeywords by remember { mutableStateOf(initial.excludeKeywords) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var appListMode by remember { mutableStateOf(initial.appListMode) }
    var appModeMenu by remember { mutableStateOf(false) }
    val packages = initial.packages
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun persist(): BlockRule = initial.copy(
        name = name.trim(),
        enabled = enabled,
        mode = mode,
        keywords = keywords,
        excludeKeywords = excludeKeywords,
        packages = packages,
        appListMode = appListMode,
    )

    fun validate(): String? {
        return when (mode) {
            MatchMode.ALL_CONTENT -> null
            MatchMode.REGEX -> if (keywords.isEmpty()) "请填写正则表达式" else null
            MatchMode.CONTAINS_A_NOT_B -> if (keywords.isEmpty()) "请至少填写一个 A 关键词" else null
            else -> if (keywords.isEmpty()) "请至少添加一个关键词" else null
        }
    }

    fun trySave() {
        val error = validate()
        if (error != null) {
            scope.launch { snackbar.showSnackbar(error) }
        } else {
            onSave(persist())
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { trySave() },
                modifier = Modifier.padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.save))
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isNew) R.string.rule_new else R.string.rule_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rule_enabled),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            ExposedDropdownMenuBox(
                expanded = modeMenuExpanded,
                onExpandedChange = { modeMenuExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    readOnly = true,
                    value = stringResource(mode.labelRes()),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.match_mode)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeMenuExpanded)
                    },
                    supportingText = { Text(stringResource(mode.hintRes())) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )
                ExposedDropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false },
                ) {
                    MatchMode.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.labelRes())) },
                            onClick = {
                                mode = item
                                modeMenuExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            when (mode) {
                MatchMode.ALL_CONTENT -> Unit
                MatchMode.REGEX -> {
                    var pattern by remember {
                        mutableStateOf(initial.keywords.firstOrNull().orEmpty())
                    }
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = {
                            pattern = it
                            keywords = listOfNotNull(it.trim().takeIf { value -> value.isNotEmpty() })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.regex_pattern)) },
                        supportingText = { Text(stringResource(R.string.regex_hint)) },
                        minLines = 2,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                MatchMode.CONTAINS_A_NOT_B -> {
                    KeywordEditor(
                        label = stringResource(R.string.keywords_a),
                        keywords = keywords,
                        onChange = { keywords = it },
                        supporting = stringResource(R.string.keywords_a_hint),
                    )
                    KeywordEditor(
                        label = stringResource(R.string.keywords_b),
                        keywords = excludeKeywords,
                        onChange = { excludeKeywords = it },
                        supporting = stringResource(R.string.keywords_b_hint),
                    )
                }
                else -> KeywordEditor(
                    label = stringResource(R.string.keywords_label),
                    keywords = keywords,
                    onChange = { keywords = it },
                    supporting = stringResource(R.string.keywords_chip_hint),
                )
            }
            if (mode == MatchMode.ALL_CONTENT && packages.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.warn_all_content),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Box {
                            TextButton(
                                onClick = { appModeMenu = true },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = stringResource(appListMode.labelRes()),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = appModeMenu,
                                onDismissRequest = { appModeMenu = false },
                            ) {
                                AppListMode.entries.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(item.labelRes())) },
                                        onClick = {
                                            appListMode = item
                                            appModeMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (packages.isEmpty()) {
                                stringResource(R.string.apps_all)
                            } else {
                                val names = packages.take(4).joinToString("、", transform = appLabel) +
                                    if (packages.size > 4) " 等 ${packages.size} 个" else ""
                                if (appListMode == AppListMode.BLACKLIST) {
                                    "排除 $names"
                                } else {
                                    names
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onPickApps(persist()) }) {
                        Text(stringResource(R.string.choose_apps))
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

private fun MatchMode.hintRes(): Int = when (this) {
    MatchMode.CONTAINS_ANY -> R.string.mode_hint_contains_any
    MatchMode.CONTAINS_ALL -> R.string.mode_hint_contains_all
    MatchMode.NOT_CONTAINS_ANY -> R.string.mode_hint_not_contains_any
    MatchMode.NOT_CONTAINS_ALL -> R.string.mode_hint_not_contains_all
    MatchMode.CONTAINS_A_NOT_B -> R.string.mode_hint_contains_a_not_b
    MatchMode.REGEX -> R.string.mode_hint_regex
    MatchMode.ALL_CONTENT -> R.string.mode_hint_all_content
}
