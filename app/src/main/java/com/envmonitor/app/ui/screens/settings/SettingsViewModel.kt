package com.envmonitor.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun updateInterval(seconds: Int) {
        _settings.value = _settings.value.copy(updateInterval = seconds)
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        _settings.value = _settings.value.copy(temperatureUnit = unit)
    }

    fun updateNotifications(enabled: Boolean) {
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }
}

data class Settings(
    val updateInterval: Int = 5,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val notificationsEnabled: Boolean = false
)

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}
