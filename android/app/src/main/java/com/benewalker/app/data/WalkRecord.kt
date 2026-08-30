package com.benewalker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "walk_records")
data class WalkRecord(
    @PrimaryKey
    val date: String, // YYYY-MM-DD
    val morningSeconds: Int = 0, // 1. Gehen
    val eveningSeconds: Int = 0, // 2. Gehen
    val totalSeconds: Int = morningSeconds + eveningSeconds,
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "manual", // "manual", "garmin_health_connect", "stopwatch"
    val morningDistanceMeters: Double = 0.0,
    val eveningDistanceMeters: Double = 0.0,
    val morningRouteJson: String? = null,
    val eveningRouteJson: String? = null
)
