package app.recess.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.recess.android.ui.components.Divider
import app.recess.android.ui.screens.ChildScreen
import app.recess.android.ui.screens.ClassroomScreen
import app.recess.android.ui.screens.HomeScreen
import app.recess.android.ui.screens.LogScreen
import app.recess.android.ui.screens.RulesScreen
import app.recess.android.ui.screens.SettingsScreen
import app.recess.android.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Classroom("classroom", "Classroom", Icons.AutoMirrored.Outlined.MenuBook),
    Rules("rules", "Rules", Icons.Outlined.Tune),
    Log("log", "Log", Icons.Outlined.History),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

private const val CHILD_ROUTE = "child/{id}"

/** Wall-clock time that ticks every 30 s, so greetings, due labels and downtime stay current. */
@Composable
private fun rememberNow(): ZonedDateTime {
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            delay(30_000)
            value = ZonedDateTime.now()
        }
    }
    return now
}

@Composable
fun RecessApp(vm: FamilyViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val usageAccess by vm.usageAccess.collectAsStateWithLifecycle()
    val now = rememberNow()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.resultCode, it.data)
    }
    LaunchedEffect(Unit) { vm.consentRequests.collect { consent.launch(IntentSenderRequest.Builder(it).build()) } }
    LaunchedEffect(Unit) {
        vm.messages.collect { m -> snackbar.showSnackbar(if (m.detail != null) "${m.title}\n${m.detail}" else m.title) }
    }
    // Pick up usage access granted in Settings, and fresh screen time, whenever the app returns.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshUsage() }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onChild = route == CHILD_ROUTE

    Scaffold(
        containerColor = Palette.Bg,
        topBar = { TopBar(showBack = onChild, onBack = { nav.popBackStack() }) },
        bottomBar = { BottomBar(nav, current = if (onChild) Tab.Home.route else route) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) {
                HomeScreen(
                    state, now, syncing, vm,
                    openChild = { nav.navigate("child/$it") },
                    openClassroom = { nav.switchTab(Tab.Classroom) },
                    openLog = { nav.switchTab(Tab.Log) },
                    openSettings = { nav.switchTab(Tab.Settings) },
                )
            }
            composable(Tab.Classroom.route) { ClassroomScreen(state, now, vm) }
            composable(Tab.Rules.route) { RulesScreen(state, vm) }
            composable(Tab.Log.route) { LogScreen(state, now) }
            composable(Tab.Settings.route) { SettingsScreen(state, syncing, usageAccess, vm) }
            composable(CHILD_ROUTE) { backStack ->
                ChildScreen(state, backStack.arguments?.getString("id").orEmpty(), now, usageAccess, vm)
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun TopBar(showBack: Boolean, onBack: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Palette.InkSoft) }
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Text("Recess", style = MaterialTheme.typography.headlineSmall)
                Text("  Classroom → screen time", style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
            }
        }
        Divider()
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = Palette.Surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.switchTab(tab) },
                modifier = Modifier.testTag("tab_${tab.route}"),
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.Accent,
                    selectedTextColor = Palette.Accent,
                    indicatorColor = Palette.AccentSoft,
                    unselectedIconColor = Palette.Muted,
                    unselectedTextColor = Palette.Muted,
                ),
            )
        }
    }
}
