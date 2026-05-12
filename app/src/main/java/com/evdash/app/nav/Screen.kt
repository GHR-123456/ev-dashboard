package com.evdash.app.nav

sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "首页")
    object NavMap : Screen("navmap", "导航")
    object Media : Screen("media", "音乐")
    object Entertainment : Screen("entertainment", "娱乐")
    object Vehicle : Screen("vehicle", "车辆")
    object Status : Screen("status", "状态")
    object Devices : Screen("devices", "设备")
    object Sniffer : Screen("sniffer", "嗅探")
    object Settings : Screen("settings", "设置")

    companion object {
        val tabs: List<Screen> = listOf(
            Home, NavMap, Media, Entertainment, Vehicle, Status, Devices, Sniffer, Settings
        )
    }
}
