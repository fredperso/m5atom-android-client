package com.envmonitor.app.ui.screens.gauges

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.envmonitor.app.data.SensorData
import com.envmonitor.app.service.BluetoothService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private const val TAG = "GaugesViewModel"

@HiltViewModel
class GaugesViewModel @Inject constructor(
    private val bluetoothService: BluetoothService
) : ViewModel() {
    val sensorData: StateFlow<SensorData> = bluetoothService.sensorData
    val isConnected: StateFlow<Boolean> = bluetoothService.isConnected

    init {
        // Monitor sensor data changes
        sensorData.onEach {
            Log.d(TAG, "Received sensor update: $it")
        }.launchIn(viewModelScope)

        // Monitor connection state
        isConnected.onEach {
            Log.d(TAG, "Connection state changed: $it")
        }.launchIn(viewModelScope)
    }
}
