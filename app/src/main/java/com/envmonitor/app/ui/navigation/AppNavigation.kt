package com.envmonitor.app.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.envmonitor.app.ui.screens.gauges.GaugesScreen
import com.envmonitor.app.ui.screens.devices.DeviceSelectionScreen
import com.envmonitor.app.ui.screens.settings.SettingsScreen

private const val TAG = "AppNavigation"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    Log.d(TAG, "Setting up NavHost with start destination: ${AppDestinations.Gauges.route}")

    NavHost(navController = navController, startDestination = AppDestinations.Gauges.route) {
        composable(AppDestinations.Gauges.route) { 
            Log.d(TAG, "Navigating to Gauges screen")
            GaugesScreen(navController) 
        }
        composable(AppDestinations.Devices.route) { 
            Log.d(TAG, "Navigating to Devices screen")
            DeviceSelectionScreen(
                navController = navController,
                onDeviceSelected = {
                    Log.d(TAG, "Device selected, popping back to Gauges")
                    navController.popBackStack()
                }
            )
        }
        composable(AppDestinations.Settings.route) { 
            Log.d(TAG, "Navigating to Settings screen")
            SettingsScreen(navController) 
        }
    }
}
