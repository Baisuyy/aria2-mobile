package com.aria2.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aria2.mobile.data.LinkParser
import com.aria2.mobile.service.Aria2Service
import com.aria2.mobile.ui.components.AppNavigationBar
import com.aria2.mobile.ui.screens.AddScreen
import com.aria2.mobile.ui.screens.BrowserScreen
import com.aria2.mobile.ui.screens.HomeScreen
import com.aria2.mobile.ui.screens.SettingsScreen
import com.aria2.mobile.ui.theme.Aria2Theme
import com.aria2.mobile.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()
    private var pendingLink by mutableStateOf<String?>(null)

    // 通知权限：用于前台服务常驻"aria2 本地服务"通知
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        pendingLink = LinkParser.parse(intent)
        setContent {
            Aria2Theme {
                Aria2App(
                    viewModel = viewModel,
                    urlToOpen = pendingLink,
                    onUrlConsumed = { pendingLink = null },
                )
            }
        }
    }

    /** singleTask 模式：App 已在后台，被链接再次唤入时走这里。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = LinkParser.parse(intent)
    }
}

@Composable
private fun Aria2App(viewModel: DownloadViewModel, urlToOpen: String?, onUrlConsumed: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 内嵌 aria2 本地服务：随开关启用/停用
    val appState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(appState.embeddedEnabled) {
        if (appState.embeddedEnabled) Aria2Service.start(context.applicationContext)
        else Aria2Service.stop(context.applicationContext)
    }

    // 收到外部链接：铵链到“新建下载”，让用户确认后一键开始
    LaunchedEffect(urlToOpen) {
        urlToOpen?.let { url ->
            navController.navigate("add?url=${Uri.encode(url)}")
            onUrlConsumed()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "settings") || currentRoute?.startsWith("browser") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppNavigationBar(currentRoute = currentRoute, onNavigate = { route -> navigateTo(navController, route) })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenAdd = { url -> navController.navigate("add?url=${(url ?: "").let { Uri.encode(it) }}") },
                    onOpenSettings = { navigateTo(navController, "settings") },
                )
            }
            composable("settings") {
                SettingsScreen(viewModel = viewModel, onBack = { navigateTo(navController, "home") })
            }
            composable(
                route = "browser?url={url}",
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
            ) { entry ->
                val raw = entry.arguments?.getString("url").orEmpty()
                val url = raw.ifBlank { null }?.let { Uri.decode(it) }
                BrowserScreen(
                    initialUrl = url,
                    onBack = { navigateTo(navController, "home") },
                    onCapture = { captured -> navController.navigate("add?url=${Uri.encode(captured)}") },
                )
            }
            composable(
                route = "add?url={url}",
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
            ) { entry ->
                val raw = entry.arguments?.getString("url").orEmpty()
                val url = raw.ifBlank { null }?.let { Uri.decode(it) }
                AddScreen(
                    viewModel = viewModel,
                    initialUrl = url,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun navigateTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}