package com.aria2.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val Items = listOf(
    NavItem("home", "下载", Icons.Filled.Download),
    NavItem("browser", "浏览器", Icons.Filled.Public),
    NavItem("settings", "设置", Icons.Filled.Settings),
)

@Composable
fun AppNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val currentTab = when {
        currentRoute == "home" -> "home"
        currentRoute == "settings" -> "settings"
        currentRoute?.startsWith("browser") == true -> "browser"
        else -> null
    }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Items.forEach { item ->
            NavigationBarItem(
                selected = item.route == currentTab,
                onClick = { if (item.route != currentTab) onNavigate(item.route) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (item.route == currentTab) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                    )
                },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.route == currentTab) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                    )
                },
            )
        }
    }
}