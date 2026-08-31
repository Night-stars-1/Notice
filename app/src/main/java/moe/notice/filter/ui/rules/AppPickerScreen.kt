package moe.notice.filter.ui.rules

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import moe.notice.filter.R
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.data.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    apps: List<InstalledApp>,
    selected: List<String>,
    appListMode: AppListMode,
    onConfirm: (List<String>, AppListMode) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(selected.toSet()) }
    var userOnly by remember { mutableStateOf(true) }
    var filterMenu by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(appListMode) }
    var listModeMenu by remember { mutableStateOf(false) }

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
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.save))
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
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
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { listModeMenu = true }) {
                        Text(stringResource(listMode.labelRes()))
                    }
                    DropdownMenu(
                        expanded = listModeMenu,
                        onDismissRequest = { listModeMenu = false },
                    ) {
                        AppListMode.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.labelRes())) },
                                onClick = {
                                    listMode = item
                                    listModeMenu = false
                                },
                                trailingIcon = {
                                    if (item == listMode) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (checked.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.apps_selected_count, checked.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val isChecked = app.packageName in checked
                    ListItem(
                        modifier = Modifier.clickable {
                            checked = if (isChecked) checked - app.packageName else checked + app.packageName
                        },
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { on ->
                                    checked = if (on) checked + app.packageName else checked - app.packageName
                                },
                            )
                        },
                    )
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
            context.packageManager.getApplicationIcon(packageName)
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
