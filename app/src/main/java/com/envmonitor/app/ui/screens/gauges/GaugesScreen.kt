package com.envmonitor.app.ui.screens.gauges

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.envmonitor.app.ui.components.GaugeComponent
import com.envmonitor.app.ui.navigation.AppDestinations
import com.envmonitor.app.data.SensorData

private const val TAG = "GaugesScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugesScreen(
    navController: NavController,
    viewModel: GaugesViewModel = hiltViewModel()
) {
    val sensorData by viewModel.sensorData.collectAsState(initial = SensorData())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Environmental Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Bluetooth/Device selection button
                    FilledTonalIconButton(
                        onClick = {
                            Log.d(TAG, "Bluetooth button clicked, navigating to Devices screen")
                            try {
                                navController.navigate(AppDestinations.Devices.route)
                                Log.d(TAG, "Navigation to Devices completed")
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation to Devices failed", e)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BluetoothSearching,
                            contentDescription = "Select Device"
                        )
                    }
                    // Settings button
                    IconButton(
                        onClick = { 
                            Log.d(TAG, "Settings button clicked")
                            navController.navigate(AppDestinations.Settings.route) 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            Spacer(modifier = Modifier.height(25.dp))
            
            GaugeComponent(
                value = sensorData.temperature,
                maxValue = 50f,
                title = "Temperature",
                unit = "°C"
            )
            
            GaugeComponent(
                value = sensorData.humidity,
                maxValue = 100f,
                title = "Humidity",
                unit = "%"
            )
            
            GaugeComponent(
                value = sensorData.pressure,
                maxValue = 1100f,
                title = "Pressure",
                unit = "hPa"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GaugesScreenPreview() {
    val navController = rememberNavController()
    GaugesScreen(navController = navController)
}
