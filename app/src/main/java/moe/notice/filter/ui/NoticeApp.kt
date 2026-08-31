package moe.notice.filter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import moe.notice.filter.R
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.ui.logs.LogsScreen
import moe.notice.filter.ui.rules.AppPickerScreen
import moe.notice.filter.ui.rules.RuleEditorScreen
import moe.notice.filter.ui.rules.RulesScreen
import moe.notice.filter.ui.theme.NoticeTheme

@Composable
fun NoticeApp(
    viewModel: NoticeViewModel,
    openLogs: Boolean = false,
    onLogsConsumed: () -> Unit = {},
) {
    NoticeTheme {
        val config by viewModel.config.collectAsStateWithLifecycle()
        val records by viewModel.records.collectAsStateWithLifecycle()
        val apps by viewModel.apps.collectAsStateWithLifecycle()
        var tab by remember { mutableIntStateOf(0) }
        LaunchedEffect(openLogs) {
            if (openLogs) {
                tab = 1
                onLogsConsumed()
            }
        }
        var draft by remember { mutableStateOf<BlockRule?>(null) }
        var isNew by remember { mutableStateOf(false) }
        var pickingApps by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val permission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                val keyword = testKeyword(config.rules)
                if (keyword != null) {
                    viewModel.sendTest(keyword)
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.test_sent)) }
                }
            } else {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.test_need_permission)) }
            }
        }

        fun requestTest() {
            val keyword = testKeyword(config.rules)
            if (keyword == null) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.test_need_keyword)) }
                return
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            viewModel.sendTest(keyword)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.test_sent)) }
        }

        val screen = when {
            pickingApps && draft != null -> Screen.Apps
            draft != null -> Screen.Editor
            else -> Screen.Home
        }
        if (screen != Screen.Home) {
            BackHandler {
                if (screen == Screen.Apps) pickingApps = false else draft = null
            }
        }
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { slideFade(forward = targetState.ordinal > initialState.ordinal) },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Apps -> AppPickerScreen(
                    apps = apps,
                    selected = draft?.packages.orEmpty(),
                    appListMode = draft?.appListMode ?: AppListMode.WHITELIST,
                    onConfirm = { packages, mode ->
                        draft = draft?.copy(packages = packages, appListMode = mode)
                        pickingApps = false
                    },
                    onBack = { pickingApps = false },
                )
                Screen.Editor -> {
                    val editing = draft ?: return@AnimatedContent
                    RuleEditorScreen(
                        initial = editing,
                        isNew = isNew,
                        appLabel = viewModel::labelFor,
                        onSave = { rule ->
                            viewModel.saveRule(rule)
                            draft = null
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.saved)) }
                        },
                        onPickApps = { next ->
                            draft = next
                            pickingApps = true
                        },
                        onBack = { draft = null },
                    )
                }
                Screen.Home -> {
                    HomeScaffold(
                        tab = tab,
                        onTabChange = { tab = it },
                        moduleActive = viewModel.moduleActive,
                        config = config,
                        records = records,
                        onRequestTest = { requestTest() },
                        onAddRule = {
                            isNew = true
                            draft = BlockRule()
                        },
                        appLabel = viewModel::labelFor,
                        onEnabledChange = viewModel::setEnabled,
                        onLogEnabledChange = viewModel::setLogEnabled,
                        onToggleRule = viewModel::toggleRule,
                        onEditRule = {
                            isNew = false
                            draft = it
                        },
                        onDeleteRule = viewModel::deleteRule,
                        onClearLogs = viewModel::clearLogs,
                        snackbar = snackbar,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScaffold(
    tab: Int,
    onTabChange: (Int) -> Unit,
    moduleActive: Boolean,
    config: FilterConfig,
    records: List<NotificationRecord>,
    onRequestTest: () -> Unit,
    onAddRule: () -> Unit,
    appLabel: (String) -> String,
    onEnabledChange: (Boolean) -> Unit,
    onLogEnabledChange: (Boolean) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (BlockRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onClearLogs: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var logQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var logFilter by remember { mutableIntStateOf(0) }
    var logAppPackage by remember { mutableStateOf<String?>(null) }
    var filterMenu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    var confirmClearLogs by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            val barColors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            if (tab == 1 && searching) {
                val logApps = remember(records) {
                    records.map { it.packageName }.distinct().sortedBy(appLabel)
                }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.statusBarsPadding().padding(bottom = 8.dp)) {
                        OutlinedTextField(
                            value = logQuery,
                            onValueChange = { logQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.search_logs)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    searching = false
                                    logQuery = ""
                                    logAppPackage = null
                                }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = logFilter == 0,
                                onClick = { logFilter = 0 },
                                label = { Text(stringResource(R.string.filter_all)) },
                            )
                            FilterChip(
                                selected = logFilter == 1,
                                onClick = { logFilter = 1 },
                                label = { Text(stringResource(R.string.filter_blocked)) },
                            )
                            FilterChip(
                                selected = logFilter == 2,
                                onClick = { logFilter = 2 },
                                label = { Text(stringResource(R.string.filter_allowed)) },
                            )
                            Box {
                                FilterChip(
                                    selected = logAppPackage != null,
                                    onClick = { appMenu = true },
                                    label = {
                                        Text(
                                            logAppPackage?.let(appLabel)
                                                ?: stringResource(R.string.apps_all),
                                        )
                                    },
                                )
                                DropdownMenu(
                                    expanded = appMenu,
                                    onDismissRequest = { appMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.apps_all)) },
                                        onClick = {
                                            logAppPackage = null
                                            appMenu = false
                                        },
                                        trailingIcon = {
                                            if (logAppPackage == null) {
                                                Icon(Icons.Outlined.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                    logApps.forEach { pkg ->
                                        DropdownMenuItem(
                                            text = { Text(appLabel(pkg)) },
                                            onClick = {
                                                logAppPackage = pkg
                                                appMenu = false
                                            },
                                            trailingIcon = {
                                                if (logAppPackage == pkg) {
                                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        if (tab == 1) {
                            IconButton(
                                onClick = { confirmClearLogs = true },
                                enabled = records.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = stringResource(R.string.clear_logs),
                                )
                            }
                            Box {
                                IconButton(onClick = { filterMenu = true }) {
                                    Icon(
                                        Icons.Outlined.FilterList,
                                        contentDescription = stringResource(R.string.filter_all),
                                    )
                                }
                                DropdownMenu(
                                    expanded = filterMenu,
                                    onDismissRequest = { filterMenu = false },
                                ) {
                                    listOf(
                                        0 to R.string.filter_all,
                                        1 to R.string.filter_blocked,
                                        2 to R.string.filter_allowed,
                                    ).forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            onClick = {
                                                logFilter = value
                                                filterMenu = false
                                            },
                                            trailingIcon = {
                                                if (logFilter == value) {
                                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { searching = true }) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search_logs),
                                )
                            }
                        } else {
                            IconButton(onClick = onRequestTest) {
                                Icon(
                                    Icons.Outlined.Science,
                                    contentDescription = stringResource(R.string.test),
                                )
                            }
                        }
                    },
                    scrollBehavior = scroll,
                    colors = barColors,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = {
                        searching = false
                        onTabChange(0)
                    },
                    icon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_rules)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTabChange(1) },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_logs)) },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = tab == 0,
                enter = scaleIn(tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAddRule,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_rule)) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { slideFade(forward = targetState > initialState) },
            label = "tab",
        ) { currentTab ->
            if (currentTab == 0) {
                RulesScreen(
                    config = config,
                    moduleActive = moduleActive,
                    appLabel = appLabel,
                    onEnabledChange = onEnabledChange,
                    onLogEnabledChange = onLogEnabledChange,
                    onToggleRule = onToggleRule,
                    onEditRule = onEditRule,
                    onDeleteRule = onDeleteRule,
                    contentPadding = padding,
                )
            } else {
                LogsScreen(
                    records = records,
                    query = logQuery,
                    filter = logFilter,
                    appPackage = if (searching) logAppPackage else null,
                    appLabel = appLabel,
                    contentPadding = padding,
                )
            }
        }
    }
    if (confirmClearLogs) {
        AlertDialog(
            onDismissRequest = { confirmClearLogs = false },
            title = { Text(stringResource(R.string.clear_logs)) },
            text = { Text(stringResource(R.string.clear_logs_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearLogs()
                    confirmClearLogs = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLogs = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private enum class Screen { Home, Editor, Apps }

private fun slideFade(forward: Boolean) = if (forward) {
    (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it / 3 } +
        fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 3 } +
            fadeOut(tween(160)))
} else {
    (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 3 } +
        fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { it / 3 } +
            fadeOut(tween(160)))
}

private fun testKeyword(rules: List<BlockRule>): String? {
    return rules.asSequence()
        .filter { it.enabled }
        .flatMap { it.keywords.asSequence() }
        .firstOrNull { it.isNotBlank() }
}
