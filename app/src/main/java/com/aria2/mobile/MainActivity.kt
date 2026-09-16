package com.aria2.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aria2.mobile.data.LinkParser
import com.aria2.mobile.ui.screens.AddScreen
import com.aria2.mobile.ui.screens.BrowserScreen
import com.aria2.mobile.ui.screens.HomeScreen
import com.aria2.mobile.ui.screens.SettingsScreen
import com.aria2.mobile.ui.theme.Aria2Theme
import com.aria2.mobile.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()
    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // 收到外部链接：铵链到“新建下载”，让用户确认后一键开始
    LaunchedEffect(urlToOpen) {
        urlToOpen?.let { url ->
            navController.navigate("add?url=${Uri.encode(url)}")
            onUrlConsumed()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenAdd = { url -> navController.navigate("add?url=${(url ?: "").let { Uri.encode(it) }}") },
                onOpenBrowser = { navController.navigate("browser?url=") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
                onCapture = { captured ->
                    // 网页里点的链接 / 下载文件：捕获后进入“新建下载”确认，参数名固定为 capture
                    navController.navigate("add?url=${Uri.encode(captured)}")
                },
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