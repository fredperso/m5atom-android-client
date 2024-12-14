package com.envmonitor.app.ui.navigation

sealed class AppDestinations(val route: String) {
    object Gauges : AppDestinations("gauges")
    object Devices : AppDestinations("devices")
    object Settings : AppDestinations("settings")
}
