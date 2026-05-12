package com.evdash.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.evdash.app.ui.entertainment.EntertainmentScreen
import com.evdash.app.ui.devices.DevicesScreen
import com.evdash.app.ui.home.HomeScreen
import com.evdash.app.ui.media.MediaScreen
import com.evdash.app.ui.navmap.NavMapScreen
import com.evdash.app.ui.settings.SettingsScreen
import com.evdash.app.ui.sniffer.SnifferScreen
import com.evdash.app.ui.status.StatusScreen
import com.evdash.app.ui.theme.Adaptive
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.SurfaceLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextMid
import com.evdash.app.ui.vehicle.VehicleScreen

@Composable
fun EvNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Home.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Left sidebar navigation
            val showLabels = Adaptive.showNavLabels
            if (Adaptive.isTiny) {
                // Tiny screen: custom scrollable sidebar with smaller items
                TinySidebar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            } else {
                val items = remember { listOf(Screen.Home, Screen.Status, Screen.NavMap, Screen.Media, Screen.Entertainment, Screen.Vehicle, Screen.Devices, Screen.Sniffer, Screen.Settings) }
                NavigationRail(
                    modifier = Modifier
                        .width(Adaptive.navRailWidth)
                        .background(SurfaceLight),
                    containerColor = SurfaceLight,
                    contentColor = TextMid
                ) {
                    items.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.padding(bottom = if (showLabels) 2.dp else 0.dp)
                                )
                            },
                            label = {
                                if (showLabels) {
                                    Text(
                                        text = screen.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier.height(0.dp)
                                    )
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                unselectedIconColor = TextDim,
                                unselectedTextColor = TextDim,
                                indicatorColor = ElectricCyan.copy(alpha = 0.22f)
                            ),
                            alwaysShowLabel = showLabels
                        )
                    }
                }
            }

            // Main content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                val navigate: (String) -> Unit = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    composable(Screen.Home.route) { HomeScreen(modifier = Modifier.fillMaxSize(), onNavigate = navigate) }
                    composable(Screen.NavMap.route) { NavMapScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Media.route) { MediaScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Entertainment.route) { EntertainmentScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Vehicle.route) { VehicleScreen(modifier = Modifier.fillMaxSize(), onNavigate = navigate) }
                    composable(Screen.Status.route) { StatusScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Devices.route) { DevicesScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Sniffer.route) { SnifferScreen(modifier = Modifier.fillMaxSize()) }
                    composable(Screen.Settings.route) { SettingsScreen(modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
private fun TinySidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(Adaptive.navRailWidth)
            .fillMaxHeight()
            .background(SurfaceLight)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top)
    ) {
        val items = remember { listOf(Screen.Home, Screen.Status, Screen.NavMap, Screen.Media, Screen.Entertainment, Screen.Vehicle, Screen.Devices, Screen.Sniffer, Screen.Settings) }
        items.forEach { screen ->
            val selected = currentRoute == screen.route
            val iconColor = if (selected) ElectricCyan else TextDim
            val bgColor = if (selected) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable { onNavigate(screen.route) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = screen.label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val Screen.icon: ImageVector
    get() = when (this) {
        Screen.Home -> Icons.Filled.Home
        Screen.NavMap -> Icons.Filled.Map
        Screen.Media -> Icons.Filled.MusicNote
        Screen.Entertainment -> Icons.Filled.DirectionsCar
        Screen.Vehicle -> Icons.Filled.Tune
        Screen.Status -> Icons.Filled.Speed
        Screen.Devices -> Icons.Filled.Bluetooth
        Screen.Sniffer -> Icons.Filled.DataObject
        Screen.Settings -> Icons.Filled.Settings
    }
