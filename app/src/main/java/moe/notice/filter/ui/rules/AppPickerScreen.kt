package moe.notice.filter.ui.rules

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import moe.notice.filter.R
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.data.InstalledApp
import moe.notice.filter.data.orSystemPackage
import moe.notice.filter.ui.theme.groupedListShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    apps: List<InstalledApp>,
    selected: List<String>,
    appListMode: AppListMode,
    onConfirm: (List<String>, AppListMode) -> Unit,
    onBack: () -> Unit,
    showModeSelector: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(selected.toSet()) }
    var userOnly by remember { mutableStateOf(true) }
    var listMode by remember { mutableStateOf(appListMode) }
    var filterMenu by remember { mutableStateOf(false) }

    val filtered = remember(apps, query, userOnly) {
        val q = query.trim()
        apps.filter { app ->
            (!userOnly || !app.isSystem) &&
                (q.isEmpty() ||
                    app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onConfirm(checked.toList().sorted(), listMode) },
                modifier = Modifier.padding(bottom = 32.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.save))
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_apps)) },
                subtitle = {
                    Text(
                        (if (showModeSelector) stringResource(listMode.labelRes()) + " · " else "") +
                            if (checked.isEmpty()) {
                                stringResource(R.string.apps_all)
                            } else {
                                stringResource(R.string.apps_selected_short, checked.size)
                            },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                        Box {
                            IconButton(onClick = { filterMenu = true }) {
                                Icon(
                                    Icons.Outlined.FilterList,
                                    contentDescription = stringResource(R.string.user_apps_only),
                                )
                            }
                            DropdownMenu(
                                expanded = filterMenu,
                                onDismissRequest = { filterMenu = false },
                            ) {
                                if (showModeSelector) {
                                    AppListMode.entries.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(item.labelRes())) },
                                            onClick = {
                                                listMode = item
                                                filterMenu = false
                                            },
                                            trailingIcon = {
                                                if (item == listMode) {
                                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.user_apps_only)) },
                                    onClick = {
                                        userOnly = !userOnly
                                        filterMenu = false
                                    },
                                    trailingIcon = {
                                        if (userOnly) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                    val isChecked = app.packageName in checked
                    Surface(
                        onClick = {
                            checked = if (isChecked) checked - app.packageName else checked + app.packageName
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupedListShape(index, filtered.size),
                        color = if (isChecked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppIcon(app.packageName)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { on ->
                                    checked = if (on) checked + app.packageName else checked - app.packageName
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName.orSystemPackage())
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(40.dp),
        )
    }
}
