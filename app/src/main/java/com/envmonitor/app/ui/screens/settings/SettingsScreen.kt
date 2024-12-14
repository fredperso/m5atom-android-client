package com.envmonitor.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Update Interval
            Text(
                text = "Update Interval",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = settings.updateInterval.toFloat(),
                onValueChange = { viewModel.updateInterval(it.toInt()) },
                valueRange = 1f..60f,
                steps = 59
            )
            Text(
                text = "${settings.updateInterval} seconds",
                style = MaterialTheme.typography.bodyMedium
            )

            Divider()

            // Temperature Unit
            Text(
                text = "Temperature Unit",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.temperatureUnit == TemperatureUnit.CELSIUS,
                    onClick = { viewModel.updateTemperatureUnit(TemperatureUnit.CELSIUS) },
                    label = { Text("Celsius") }
                )
                FilterChip(
                    selected = settings.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                    onClick = { viewModel.updateTemperatureUnit(TemperatureUnit.FAHRENHEIT) },
                    label = { Text("Fahrenheit") }
                )
            }

            Divider()

            // Notifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Notifications",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { viewModel.updateNotifications(it) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    val navController = rememberNavController()
    SettingsScreen(navController = navController)
}
