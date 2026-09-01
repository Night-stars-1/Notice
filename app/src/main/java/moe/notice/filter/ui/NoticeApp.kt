package moe.notice.filter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MotionScheme
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
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
import moe.notice.filter.data.InstalledApp
import moe.notice.filter.data.SYSTEM_PACKAGE
import moe.notice.filter.data.orSystemPackage
import moe.notice.filter.domain.Appearance
import moe.notice.filter.domain.AppListMode
import moe.notice.filter.domain.DarkMode
import moe.notice.filter.domain.SpamExplainer
import moe.notice.filter.domain.SpamJudge
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.NotificationRecord
import moe.notice.filter.ui.logs.DebugLogScreen
import moe.notice.filter.ui.logs.LogsScreen
import moe.notice.filter.ui.rules.AppPickerScreen
import moe.notice.filter.ui.rules.RuleEditorScreen
import moe.notice.filter.ui.rules.RulesScreen
import moe.notice.filter.ui.settings.SettingsScreen
import moe.notice.filter.ui.theme.NoticeTheme
import moe.notice.filter.ui.theme.isDark

@Composable
fun NoticeApp(
    viewModel: NoticeViewModel,
    openLogs: Boolean = false,
    onLogsConsumed: () -> Unit = {},
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    NoticeTheme(appearance = appearance) {
        SystemBarsEffect(dark = appearance.isDark(), background = MaterialTheme.colorScheme.surface)
        val config by viewModel.config.collectAsStateWithLifecycle()
        val records by viewModel.records.collectAsStateWithLifecycle()
        val apps by viewModel.apps.collectAsStateWithLifecycle()
        val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
        val labels by viewModel.labels.collectAsStateWithLifecycle()
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
        var pickingSpamApps by remember { mutableStateOf(false) }
        var pickingLogApps by remember { mutableStateOf(false) }
        var showDebugLog by remember { mutableStateOf(false) }
        val debugLines by viewModel.debugLines.collectAsStateWithLifecycle()
        var logAppPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
        }
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
            showDebugLog -> Screen.DebugLog
            pickingSpamApps || pickingLogApps -> Screen.Apps
            pickingApps && draft != null -> Screen.Apps
            draft != null -> Screen.Editor
            else -> Screen.Home
        }
        if (screen != Screen.Home) {
            BackHandler {
                when {
                    showDebugLog -> showDebugLog = false
                    pickingSpamApps -> pickingSpamApps = false
                    pickingLogApps -> pickingLogApps = false
                    screen == Screen.Apps -> pickingApps = false
                    else -> draft = null
                }
            }
        }
        val motion = MaterialTheme.motionScheme
        val axisOffset = with(LocalDensity.current) { SHARED_AXIS_OFFSET.roundToPx() }
        AnimatedContent(
            targetState = screen,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            transitionSpec = {
                sharedAxisX(motion, forward = targetState.ordinal > initialState.ordinal) { axisOffset }
            },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.DebugLog -> DebugLogScreen(
                    lines = debugLines,
                    appLabel = viewModel::labelFor,
                    onClear = viewModel::clearDebugLog,
                    onBack = { showDebugLog = false },
                )
                Screen.Apps -> if (pickingLogApps) {
                    // 仅列出出现在日志中的应用；已卸载的应用回退为显示包名。
                    val logApps = remember(records, apps) {
                        records.map { it.packageName.orSystemPackage() }.distinct().map { pkg ->
                            apps.firstOrNull { it.packageName == pkg }
                                ?: InstalledApp(
                                    packageName = pkg,
                                    label = viewModel.labelFor(pkg),
                                    isSystem = pkg == SYSTEM_PACKAGE,
                                )
                        }.sortedBy { it.label }
                    }
                    AppPickerScreen(
                        apps = logApps,
                        selected = logAppPackages.toList(),
                        appListMode = AppListMode.WHITELIST,
                        onConfirm = { packages, _ ->
                            logAppPackages = packages.toSet()
                            pickingLogApps = false
                        },
                        onBack = { pickingLogApps = false },
                        showModeSelector = false,
                    )
                } else if (pickingSpamApps) {
                    AppPickerScreen(
                        apps = apps,
                        selected = config.spamExcludedPackages,
                        appListMode = AppListMode.BLACKLIST,
                        onConfirm = { packages, _ ->
                            viewModel.setSpamExcludedPackages(packages)
                            pickingSpamApps = false
                        },
                        onBack = { pickingSpamApps = false },
                        showModeSelector = false,
                    )
                } else {
                    AppPickerScreen(
                        apps = apps,
                        selected = draft?.packages.orEmpty(),
                        appListMode = draft?.appListMode ?: AppListMode.WHITELIST,
                        onConfirm = { packages, mode ->
                            draft = draft?.copy(packages = packages, appListMode = mode)
                            pickingApps = false
                        },
                        onBack = { pickingApps = false },
                    )
                }
                Screen.Editor -> {
                    val editing = draft ?: return@AnimatedContent
                    RuleEditorScreen(
                        initial = editing,
                        isNew = isNew,
                        appLabel = viewModel::labelFor,
                        onSave = { rule ->
                            if (viewModel.saveRule(rule)) {
                                draft = null
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.saved)) }
                            }
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
                        moduleActive = moduleStatus.active,
                        xposedApi = moduleStatus.apiVersion,
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
                        onSpamEnabledChange = viewModel::setSpamEnabled,
                        onSpamThresholdChange = viewModel::setSpamThreshold,
                        onOpenDebugLog = { showDebugLog = true },
                        onDebugLogEnabledChange = viewModel::setDebugLogEnabled,
                        onJudgeLogEnabledChange = viewModel::setJudgeLogEnabled,
                        appearance = appearance,
                        onDarkModeChange = viewModel::setDarkMode,
                        onDynamicColorChange = viewModel::setDynamicColor,
                        onThemeColorChange = viewModel::setThemeColor,
                        spamExcludedCount = config.spamExcludedPackages.size,
                        onPickSpamApps = { pickingSpamApps = true },
                        tuneSampleCount = labels.size,
                        onClearLabels = viewModel::clearLabels,
                        labels = labels.mapValues { it.value.spam },
                        onLabel = viewModel::setLabel,
                        explain = viewModel::explain,
                        onRescore = viewModel::rescore,
                        logAppPackages = logAppPackages,
                        onPickLogApps = { pickingLogApps = true },
                        onClearLogApps = { logAppPackages = emptySet() },
                        onToggleRule = viewModel::toggleRule,
                        onEditRule = {
                            isNew = false
                            draft = it
                        },
                        onDeleteRule = viewModel::deleteRule,
                        onReorderRules = viewModel::reorderRules,
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
    xposedApi: Int,
    config: FilterConfig,
    records: List<NotificationRecord>,
    onRequestTest: () -> Unit,
    onAddRule: () -> Unit,
    appLabel: (String) -> String,
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
    labels: Map<String, Boolean>,
    onLabel: (NotificationRecord, Boolean?) -> Unit,
    explain: (NotificationRecord) -> SpamExplainer.Explanation?,
    onRescore: (NotificationRecord) -> SpamJudge.Verdict?,
    logAppPackages: Set<String>,
    onPickLogApps: () -> Unit,
    onClearLogApps: () -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (BlockRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onReorderRules: (List<String>) -> Unit,
    onClearLogs: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var logQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var logFilter by remember { mutableIntStateOf(0) }
    var filterMenu by remember { mutableStateOf(false) }
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
                                    onClearLogApps()
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
                            FilterChip(
                                selected = logFilter == 3,
                                onClick = { logFilter = 3 },
                                label = { Text(stringResource(R.string.filter_ai)) },
                            )
                            FilterChip(
                                selected = logAppPackages.isNotEmpty(),
                                onClick = onPickLogApps,
                                label = {
                                    Text(
                                        when (logAppPackages.size) {
                                            0 -> stringResource(R.string.apps_all)
                                            1 -> appLabel(logAppPackages.first())
                                            else -> stringResource(R.string.apps_selected_short, logAppPackages.size)
                                        },
                                    )
                                },
                            )
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
                                        3 to R.string.filter_ai,
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
                        }
                    },
                    scrollBehavior = scroll,
                    colors = barColors,
                )
            }
        },
        bottomBar = {
            ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                ShortNavigationBarItem(
                    selected = tab == 0,
                    onClick = {
                        searching = false
                        onTabChange(0)
                    },
                    icon = {
                        Icon(
                            if (tab == 0) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_rules)) },
                )
                ShortNavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTabChange(1) },
                    icon = {
                        Icon(
                            if (tab == 1) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_logs)) },
                )
                ShortNavigationBarItem(
                    selected = tab == 2,
                    onClick = {
                        searching = false
                        onTabChange(2)
                    },
                    icon = {
                        Icon(
                            if (tab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.tab_settings)) },
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
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = tab,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            transitionSpec = { sharedAxisX(motion, forward = targetState > initialState) { it / 4 } },
            label = "tab",
        ) { currentTab ->
            when (currentTab) {
                0 -> RulesScreen(
                    config = config,
                    moduleActive = moduleActive,
                    xposedApi = xposedApi,
                    appLabel = appLabel,
                    onToggleRule = onToggleRule,
                    onEditRule = onEditRule,
                    onDeleteRule = onDeleteRule,
                    onReorderRules = onReorderRules,
                    contentPadding = padding,
                )
                1 -> LogsScreen(
                    records = records,
                    query = logQuery,
                    filter = logFilter,
                    appPackages = if (searching) logAppPackages else emptySet(),
                    appLabel = appLabel,
                    labels = labels,
                    onLabel = onLabel,
                    explain = explain,
                    onRescore = onRescore,
                    contentPadding = padding,
                )
                else -> SettingsScreen(
                    config = config,
                    onEnabledChange = onEnabledChange,
                    onLogEnabledChange = onLogEnabledChange,
                    onSpamEnabledChange = onSpamEnabledChange,
                    onSpamThresholdChange = onSpamThresholdChange,
                    onOpenDebugLog = onOpenDebugLog,
                    onDebugLogEnabledChange = onDebugLogEnabledChange,
                    onJudgeLogEnabledChange = onJudgeLogEnabledChange,
                    appearance = appearance,
                    onDarkModeChange = onDarkModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onThemeColorChange = onThemeColorChange,
                    spamExcludedCount = spamExcludedCount,
                    onPickSpamApps = onPickSpamApps,
                    tuneSampleCount = tuneSampleCount,
                    onClearLabels = onClearLabels,
                    onRequestTest = onRequestTest,
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

private enum class Screen { Home, Editor, Apps, DebugLog }

/** 屏幕之间前进/后退导航所用的 M3 shared-axis X 位移。 */
private val SHARED_AXIS_OFFSET = 30.dp

/**
 * M3 shared-axis X：水平滑动加淡入淡出，使用 expressive 弹簧动画。[offset] 将整个宽度映射为滑动距离——
 * 屏幕之间的 push/pop 为 30dp，标签页之间为宽度的四分之一。
 */
private fun sharedAxisX(motion: MotionScheme, forward: Boolean, offset: (fullWidth: Int) -> Int): ContentTransform {
    val direction = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(motion.defaultSpatialSpec()) { direction * offset(it) } +
            fadeIn(motion.defaultEffectsSpec()),
        initialContentExit = slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * offset(it) } +
            fadeOut(motion.fastEffectsSpec()),
        sizeTransform = SizeTransform(clip = false),
    )
}

private fun testKeyword(rules: List<BlockRule>): String? {
    return rules.asSequence()
        .filter { it.enabled }
        .flatMap { it.keywords.asSequence() }
        .firstOrNull { it.isNotBlank() }
}

/** 让状态栏/导航栏图标的对比度与应用自身的深色模式选择保持一致。 */
@Composable
private fun SystemBarsEffect(dark: Boolean, background: Color) {
    val context = LocalContext.current
    SideEffect {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>()
            .firstOrNull() ?: return@SideEffect
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        activity.window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    }
}
