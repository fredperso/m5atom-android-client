package com.envmonitor.app.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.envmonitor.app.data.SensorData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    companion object {
        private const val TAG = "BluetoothService"
        // Custom service UUID for our environmental sensor
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        // Characteristic UUIDs from the working Java app
        private val TEMPERATURE_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val HUMIDITY_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        private val PRESSURE_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        // Use the standard Android constant for enabling notifications
        private val ENABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        // Timeout for descriptor write
        private const val DESCRIPTOR_WRITE_TIMEOUT = 2000L // 2 seconds
    }

    private var pendingCallbacks = mutableMapOf<UUID, () -> Unit>()

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                _permissionGranted.value = false
                return
            }

            val statusMessage = when (status) {
                BluetoothGatt.GATT_SUCCESS -> "SUCCESS"
                133 -> "GATT_ERROR (133) - Authentication failure or insufficient authorization"
                8 -> "GATT_CONN_TIMEOUT (8)"
                19 -> "GATT_CONN_TERMINATE_PEER_USER (19)"
                else -> "Unknown error $status"
            }
            
            val stateMessage = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "Unknown state $newState"
            }
            
            Log.d(TAG, "onConnectionStateChange - status: $statusMessage, newState: $stateMessage")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Connected to GATT server. Attempting to discover services...")
                        _isConnected.value = true
                        try {
                            // Request a higher connection priority for faster communication
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            // Discover services after a short delay to ensure connection is stable
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Security exception while setting connection priority: ${e.message}")
                            _permissionGranted.value = false
                        }
                    } else {
                        Log.e(TAG, "Connection completed with error status: $statusMessage")
                        closeConnection()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _isConnected.value = false
                    closeConnection()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered - status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully")
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Custom service not found! Available services:")
                    gatt.services.forEach { discoveredService -> 
                        Log.d(TAG, "Service: ${discoveredService.uuid}")
                        discoveredService.characteristics.forEach { characteristic ->
                            Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                            characteristic.descriptors.forEach { descriptor ->
                                Log.d(TAG, "    Descriptor: ${descriptor.uuid}")
                            }
                        }
                    }
                    // Retry connection after a short delay
                    gatt.disconnect()
                    return
                }
                
                // Log all characteristics in our service
                Log.d(TAG, "Found custom service ${service.uuid}, checking characteristics:")
                service.characteristics.forEach { characteristic ->
                    Log.d(TAG, "Found characteristic: ${characteristic.uuid}")
                    when (characteristic.uuid) {
                        TEMPERATURE_UUID -> Log.d(TAG, "  This is the temperature characteristic")
                        HUMIDITY_UUID -> Log.d(TAG, "  This is the humidity characteristic")
                        PRESSURE_UUID -> Log.d(TAG, "  This is the pressure characteristic")
                        else -> Log.d(TAG, "  Unknown characteristic")
                    }
                    Log.d(TAG, "  Properties: ${characteristic.properties}")
                    Log.d(TAG, "  Permissions: ${characteristic.permissions}")
                    characteristic.descriptors.forEach { descriptor ->
                        Log.d(TAG, "  Descriptor: ${descriptor.uuid}")
                    }
                }
                
                Log.d(TAG, "Found custom service, enabling notifications...")
                enableNotifications(gatt)
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                // Retry connection after a short delay
                gatt.disconnect()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // For older Android versions
            val value = characteristic.value
            onCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "============================================")
            Log.d(TAG, "onCharacteristicChanged CALLED!")
            Log.d(TAG, "Characteristic UUID: ${characteristic.uuid}")
            Log.d(TAG, "Raw bytes: ${value.joinToString(", ") { it.toString() }}")
            Log.d(TAG, "Raw hex: ${value.joinToString("") { "%02X".format(it) }}")
            Log.d(TAG, "============================================")
            
            try {
                val stringValue = String(value)
                Log.d(TAG, "Received data as string: $stringValue")
                
                val characteristicUuid = characteristic.uuid
                // Clean up the value string by removing non-numeric characters except decimal point and minus
                val cleanValue = stringValue.replace(Regex("[^\\d.-]"), "")
                Log.d(TAG, "Cleaned value: $cleanValue")
                
                val numericValue = cleanValue.toFloat()
                Log.d(TAG, "Parsed numeric value: $numericValue")
                
                when (characteristicUuid) {
                    TEMPERATURE_UUID -> {
                        Log.d(TAG, "Updating temperature to: $numericValue")
                        _sensorData.value = _sensorData.value.copy(temperature = numericValue)
                        Log.d(TAG, "New sensor data: ${_sensorData.value}")
                    }
                    HUMIDITY_UUID -> {
                        Log.d(TAG, "Updating humidity to: $numericValue")
                        _sensorData.value = _sensorData.value.copy(humidity = numericValue)
                        Log.d(TAG, "New sensor data: ${_sensorData.value}")
                    }
                    PRESSURE_UUID -> {
                        // Convert pressure from Pa to hPa (divide by 100)
                        val pressureValue = numericValue / 100.0f
                        Log.d(TAG, "Updating pressure to: $pressureValue")
                        _sensorData.value = _sensorData.value.copy(pressure = pressureValue)
                        Log.d(TAG, "New sensor data: ${_sensorData.value}")
                    }
                }
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Error parsing numeric value: ${e.message}")
                Log.e(TAG, "Failed value was: ${String(value)}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing characteristic data: ${e.message}")
                Log.e(TAG, "Stack trace:", e)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicRead - uuid: ${characteristic.uuid}, status: $status, value: ${value.contentToString()}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onCharacteristicChanged(gatt, characteristic, value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val characteristicUuid = descriptor.characteristic.uuid
            Log.d(TAG, "============================================")
            Log.d(TAG, "onDescriptorWrite called")
            Log.d(TAG, "Characteristic: $characteristicUuid")
            Log.d(TAG, "Status: $status")
            Log.d(TAG, "Has pending callback: ${pendingCallbacks.containsKey(characteristicUuid)}")
            Log.d(TAG, "============================================")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully enabled notifications for characteristic: $characteristicUuid")
                pendingCallbacks[characteristicUuid]?.invoke()
                pendingCallbacks.remove(characteristicUuid)
            } else {
                Log.e(TAG, "Failed to write descriptor for characteristic: $characteristicUuid, status: $status")
                pendingCallbacks.remove(characteristicUuid)
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "Connect called for device: ${device.address}")
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted. Required permissions:")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.e(TAG, "BLUETOOTH_CONNECT: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)}")
                Log.e(TAG, "BLUETOOTH_SCAN: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)}")
            } else {
                Log.e(TAG, "BLUETOOTH: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)}")
                Log.e(TAG, "BLUETOOTH_ADMIN: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)}")
                Log.e(TAG, "ACCESS_FINE_LOCATION: ${ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)}")
            }
            _permissionGranted.value = false
            return
        }

        try {
            Log.d(TAG, "Permissions granted, attempting to connect to device: ${device.address}")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            _permissionGranted.value = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while connecting: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            _permissionGranted.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while connecting: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            _permissionGranted.value = false
        }
    }

    fun disconnect() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            _permissionGranted.value = false
            return
        }

        try {
            bluetoothGatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while disconnecting: ${e.message}")
            _permissionGranted.value = false
        }
    }

    fun closeConnection() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            _permissionGranted.value = false
            return
        }

        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            _isConnected.value = false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while closing connection: ${e.message}")
            _permissionGranted.value = false
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            _permissionGranted.value = false
            return
        }

        try {
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                // Enable notifications for each characteristic
                enableCharacteristicNotification(gatt, service, TEMPERATURE_UUID) {
                    enableCharacteristicNotification(gatt, service, HUMIDITY_UUID) {
                        enableCharacteristicNotification(gatt, service, PRESSURE_UUID, null)
                    }
                }
            } else {
                Log.e(TAG, "Environmental service not found")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while enabling notifications: ${e.message}")
            _permissionGranted.value = false
        }
    }

    private fun enableCharacteristicNotification(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        characteristicUuid: UUID,
        onComplete: (() -> Unit)?
    ) {
        Log.d(TAG, "Enabling notifications for characteristic: $characteristicUuid")
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted for enabling notifications")
            _permissionGranted.value = false
            return
        }

        try {
            val characteristic = service.getCharacteristic(characteristicUuid)
            if (characteristic != null) {
                Log.d(TAG, "Found characteristic: $characteristicUuid")
                Log.d(TAG, "Properties: ${characteristic.properties}")
                Log.d(TAG, "Permissions: ${characteristic.permissions}")
                
                onComplete?.let { callback ->
                    pendingCallbacks[characteristicUuid] = callback
                }
                
                // First enable notifications at the Android level
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    Log.e(TAG, "Failed to enable notifications for characteristic: $characteristicUuid")
                    pendingCallbacks.remove(characteristicUuid)
                    return
                }
                
                Log.d(TAG, "Successfully enabled local notifications, setting up descriptor")
                
                // Then write to descriptor to enable notifications on the device
                val descriptor = characteristic.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    Log.d(TAG, "Found descriptor, writing enable value")
                    try {
                        descriptor.value = ENABLE_NOTIFICATION_VALUE
                        if (!gatt.writeDescriptor(descriptor)) {
                            Log.e(TAG, "Failed to write to descriptor for characteristic: $characteristicUuid")
                            pendingCallbacks.remove(characteristicUuid)
                            // Try to disable the local notification since descriptor write failed
                            gatt.setCharacteristicNotification(characteristic, false)
                        } else {
                            Log.d(TAG, "Successfully initiated descriptor write for: $characteristicUuid")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing descriptor: ${e.message}")
                        pendingCallbacks.remove(characteristicUuid)
                        gatt.setCharacteristicNotification(characteristic, false)
                    }
                } else {
                    Log.e(TAG, "CCCD descriptor not found for characteristic: $characteristicUuid")
                    pendingCallbacks.remove(characteristicUuid)
                    gatt.setCharacteristicNotification(characteristic, false)
                }
            } else {
                Log.e(TAG, "Characteristic not found: $characteristicUuid")
                pendingCallbacks.remove(characteristicUuid)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while enabling notifications: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            _permissionGranted.value = false
            pendingCallbacks.remove(characteristicUuid)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while enabling notifications: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            pendingCallbacks.remove(characteristicUuid)
        }
    }
}
