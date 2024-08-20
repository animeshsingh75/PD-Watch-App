package com.asu.pddata.model

data class DataCollectionModel(
    val timestamp: String,
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val angularX: Float,
    val angularY: Float,
    val angularZ: Float,
    val heartRate: Float,
    val medication:Int
)
