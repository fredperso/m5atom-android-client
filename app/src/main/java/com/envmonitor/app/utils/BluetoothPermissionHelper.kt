package com.envmonitor.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object BluetoothPermissionHelper {
    private const val TAG = "BluetoothPermissionHelper"

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                // Include BLUETOOTH and BLUETOOTH_ADMIN for backward compatibility
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        val hasAllPermissions = permissions.all { permission ->
            val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission check - $permission: $isGranted")
            isGranted
        }
        Log.d(TAG, "All required permissions granted: $hasAllPermissions")
        return hasAllPermissions
    }
}
