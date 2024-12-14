package com.envmonitor.app.ui.screens.devices

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.envmonitor.app.utils.BluetoothPermissionHelper

private const val TAG = "DeviceSelectionScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionScreen(
    navController: NavController,
    onDeviceSelected: () -> Unit,
    viewModel: DeviceSelectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devices = viewModel.devices.collectAsState()
    val selectedDevice = viewModel.selectedDevice.collectAsState()
    val permissionsGranted = viewModel.permissionsGranted.collectAsState()
    val isScanning = viewModel.isScanning.collectAsState()
    val isConnected = viewModel.isConnected.collectAsState()
    val errorMessage = viewModel.errorMessage.collectAsState()
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.checkPermissions()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted.value) {
            permissionLauncher.launch(BluetoothPermissionHelper.getRequiredPermissions())
        }
    }

    // Handle device connection state
    LaunchedEffect(isConnected.value) {
        if (isConnected.value) {
            Log.d(TAG, "Device connected, waiting for user action")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Device") },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            Log.d(TAG, "Back button clicked")
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isConnected.value) {
                ConnectedDeviceCard(
                    device = selectedDevice.value,
                    onDisconnect = { 
                        Log.d(TAG, "Disconnecting device")
                        viewModel.disconnect() 
                    }
                )
                
                Button(
                    onClick = {
                        Log.d(TAG, "Connected device confirmed, returning to gauges")
                        onDeviceSelected()
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Continue to Gauges")
                }
            }
            
            errorMessage.value?.let { error ->
                ErrorCard(error = error)
            }

            if (!permissionsGranted.value) {
                Button(
                    onClick = {
                        permissionLauncher.launch(BluetoothPermissionHelper.getRequiredPermissions())
                    }
                ) {
                    Text("Grant Permissions")
                }
            } else if (!isConnected.value) {
                DeviceList(
                    devices = devices.value,
                    isScanning = isScanning.value,
                    onScanClick = { viewModel.startScan() },
                    onDeviceClick = { device -> viewModel.selectDevice(device) }
                )
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: BluetoothDevice?,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Connected to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                device?.let {
                    Text(
                        text = it.name ?: it.address,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = "Disconnect",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceInfo>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning
        ) {
            Text(if (isScanning) "Scanning..." else "Scan for Devices")
        }

        devices.forEach { deviceInfo ->
            DeviceItem(
                device = deviceInfo.device,
                onClick = { onDeviceClick(deviceInfo.device) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
