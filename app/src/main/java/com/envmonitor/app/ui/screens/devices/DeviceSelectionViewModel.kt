package com.envmonitor.app.ui.screens.devices

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.envmonitor.app.service.BluetoothService
import com.envmonitor.app.utils.BluetoothPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceSelectionVM"
private const val SCAN_PERIOD = 10000L // 10 seconds

data class DeviceInfo(
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val isConnected: Boolean = false
)

@HiltViewModel
class DeviceSelectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val bluetoothService: BluetoothService
) : ViewModel() {
    
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isConnected: StateFlow<Boolean> = bluetoothService.isConnected

    private var scanJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name
            if (deviceName != null && deviceName.isNotEmpty()) {
                Log.d(TAG, "Found device: $deviceName (${device.address}) RSSI: ${result.rssi}")
                val existingDeviceIndex = _devices.value.indexOfFirst { deviceInfo -> deviceInfo.device.address == device.address }
                if (existingDeviceIndex != -1) {
                    // Update existing device
                    val updatedDevices = _devices.value.toMutableList()
                    updatedDevices[existingDeviceIndex] = updatedDevices[existingDeviceIndex].copy(rssi = result.rssi)
                    _devices.value = updatedDevices
                } else {
                    // Add new device
                    _devices.value = _devices.value + DeviceInfo(device, result.rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            stopScan()
        }
    }

    init {
        checkPermissions()
        // Monitor connection state
        viewModelScope.launch {
            bluetoothService.isConnected.collect { connected ->
                Log.d(TAG, "Connection state changed: $connected")
                // Update UI state based on connection
                _selectedDevice.value?.let { device ->
                    val updatedDevices = _devices.value.map { deviceInfo ->
                        if (deviceInfo.device.address == device.address) {
                            deviceInfo.copy(isConnected = connected)
                        } else {
                            deviceInfo
                        }
                    }
                    _devices.value = updatedDevices
                }
            }
        }
    }

    fun checkPermissions() {
        _permissionsGranted.value = BluetoothPermissionHelper.hasRequiredPermissions(context)
        if (!_permissionsGranted.value) {
            _errorMessage.value = "Bluetooth permissions are required to scan for devices"
        } else {
            _errorMessage.value = null
        }
    }

    fun startScan() {
        if (!_permissionsGranted.value) {
            _errorMessage.value = "Cannot start scan: Bluetooth permissions not granted"
            return
        }
        if (_isScanning.value) {
            _errorMessage.value = "Scan already in progress"
            return
        }
        
        try {
            _devices.value = emptyList() // Clear previous devices
            _isScanning.value = true
            
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e(TAG, "BluetoothLeScanner not available")
                _isScanning.value = false
                _errorMessage.value = "Bluetooth is not available on this device"
                return
            }

            // Configure scan settings for low latency
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Start scanning
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "Started BLE scan")
            
            // Stop scan after SCAN_PERIOD
            scanJob = viewModelScope.launch {
                delay(SCAN_PERIOD)
                stopScan()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during scan: ${e.message}")
            _permissionsGranted.value = false
            _isScanning.value = false
            _errorMessage.value = "Permission denied: Cannot scan for devices"
        } catch (e: Exception) {
            Log.e(TAG, "Error during scan: ${e.message}")
            _isScanning.value = false
            _errorMessage.value = "Failed to start scan: ${e.message}"
        }
    }

    fun stopScan() {
        if (_isScanning.value) {
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
                Log.d(TAG, "Stopped BLE scan")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception stopping scan: ${e.message}")
                _permissionsGranted.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan: ${e.message}")
            } finally {
                _isScanning.value = false
                scanJob?.cancel()
                scanJob = null
            }
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        Log.d(TAG, "Selecting device: ${device.address}")
        stopScan() // Stop scanning before connecting
        if (!_permissionsGranted.value) {
            Log.e(TAG, "Cannot connect: permissions not granted")
            _errorMessage.value = "Cannot connect: Bluetooth permissions not granted"
            return
        }
        _selectedDevice.value = device
        _errorMessage.value = null
        // Connect to the device using BluetoothService
        viewModelScope.launch {
            delay(100) // Give a small delay for scan to fully stop
            try {
                bluetoothService.connect(device)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device: ${e.message}")
                _errorMessage.value = "Failed to connect to device: ${e.message}"
                _selectedDevice.value = null
            }
        }
    }

    fun disconnectDevice(device: BluetoothDevice) {
        Log.d(TAG, "Disconnecting device: ${device.address}")
        if (device.address == _selectedDevice.value?.address) {
            bluetoothService.disconnect()
            _selectedDevice.value = null
        }
        // Update device list to show as disconnected
        val updatedDevices = _devices.value.map { deviceInfo ->
            if (deviceInfo.device.address == device.address) {
                deviceInfo.copy(isConnected = false)
            } else {
                deviceInfo
            }
        }
        _devices.value = updatedDevices
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                bluetoothService.disconnect()
                _selectedDevice.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to disconnect: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        // Don't disconnect the device when the ViewModel is cleared
        // This allows the connection to persist when navigating to the GaugesScreen
    }
}
