package com.envmonitor.app.data.model

data class SensorData(
    val id: String,
    val value: Float,
    val unit: String,
    val timestamp: Long
)
