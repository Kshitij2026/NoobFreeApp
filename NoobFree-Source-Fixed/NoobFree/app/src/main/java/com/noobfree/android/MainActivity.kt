package com.noobfree.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noobfree.android.ui.AnimationScreen
import com.noobfree.android.ui.AppManagementScreen
import com.noobfree.android.ui.DeveloperScreen
import com.noobfree.android.ui.DeviceInfoScreen
import com.noobfree.android.ui.GlassBackgroundCanvas
import com.noobfree.android.ui.HomeScreen
import com.noobfree.android.ui.PrivacyInspectorScreen
import com.noobfree.android.ui.SettingsScreen
import com.noobfree.android.ui.theme.GlassPalette
import com.noobfree.android.ui.theme.NoobFreeTheme
import com.noobfree.android.viewmodel.MainViewModel

private data class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    BottomNavDestination("home", "Dashboard", Icons.Outlined.Home),
    BottomNavDestination("privacy", "Admin & Policy", Icons.Outlined.Security),
    BottomNavDestination("apps", "Apps & Wallpaper", Icons.Outlined.Apps),
    BottomNavDestination("developer", "Dev Options", Icons.Outlined.DeveloperMode),
    BottomNavDestination("settings", "Settings", Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NoobFreeApplication
        val factory = MainViewModel.Factory(app.shizukuRepository, app.deviceRepository)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDark.takeIf { it } ?: true) }

            NoobFreeTheme(darkTheme = darkTheme) {
                GlassBackgroundCanvas(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        val mainViewModel: MainViewModel = viewModel(factory = factory)
                        NoobFreeNavHost(
                            viewModel = mainViewModel,
                            darkTheme = darkTheme,
                            onToggleDarkTheme = { darkTheme = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoobFreeNavHost(
    viewModel: MainViewModel,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllStates(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        containerColor = Color.Transparent,
        bottomBar = {
            GlassBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    HomeScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToDeveloper = { navController.navigate("developer") },
                        onNavigateToAnimation = { navController.navigate("animation") },
                        onNavigateToDeviceInfo = { navController.navigate("device") },
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToApps = { navController.navigate("apps") },
                        onNavigateToPrivacy = { navController.navigate("privacy") }
                    )
                }
                composable("developer") {
                    DeveloperScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("animation") {
                    AnimationScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("device") {
                    DeviceInfoScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("apps") {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as NoobFreeApplication
                    AppManagementScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        commandExecutor = app.shizukuRepository,
                        adminPolicyManager = app.adminPolicyManager,
                        wallpaperHelper = app.wallpaperHelper,
                        onNavigateToPrivacy = { navController.navigate("privacy") },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("privacy") {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as NoobFreeApplication
                    PrivacyInspectorScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        adminPolicyManager = app.adminPolicyManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        darkTheme = darkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xD90F172A),
                        Color(0xF2060B19)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        GlassPalette.CyanGlow.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.25f),
                        GlassPalette.VioletGlow.copy(alpha = 0.35f)
                    )
                ),
                shape = shape
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            TOP_LEVEL_DESTINATIONS.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(dest.route) },
                    icon = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label
                        )
                    },
                    label = {
                        Text(
                            text = dest.label,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GlassPalette.CyanGlow,
                        selectedTextColor = GlassPalette.CyanGlow,
                        indicatorColor = GlassPalette.CyanGlow.copy(alpha = 0.16f),
                        unselectedIconColor = GlassPalette.TextSecondary,
                        unselectedTextColor = GlassPalette.TextSecondary
                    )
                )
            }
        }
    }
}
