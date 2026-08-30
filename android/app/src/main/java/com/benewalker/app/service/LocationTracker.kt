package com.benewalker.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class KilometerSplit(
    val kmNumber: Int,
    val splitSeconds: Int,
    val totalElapsedSeconds: Int,
    val paceString: String // z.B. "09:45"
)

data class TrainingTrackingState(
    val isTracking: Boolean = false,
    val isGpsActive: Boolean = false,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentAltitude: Double = 0.0,
    val accuracy: Float = 0f,
    val totalDistanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val currentPaceMinPerKm: Double = 0.0,
    val avgPaceMinPerKm: Double = 0.0,
    val routePoints: List<GpsPoint> = emptyList(),
    val splits: List<KilometerSplit> = emptyList()
)

class LocationTracker(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _trackingState = MutableStateFlow(TrainingTrackingState())
    val trackingState: StateFlow<TrainingTrackingState> = _trackingState.asStateFlow()

    private var isListening = false
    private var lastTrackedLocation: Location? = null
    private var lastSplitElapsedSec: Int = 0
    private var currentElapsedSeconds: Int = 0

    fun updateElapsedSeconds(elapsedSec: Int) {
        currentElapsedSeconds = elapsedSec
        if (_trackingState.value.isTracking && _trackingState.value.totalDistanceMeters > 0) {
            val distKm = _trackingState.value.totalDistanceMeters / 1000.0
            val avgSpeed = if (elapsedSec > 0) (distKm / (elapsedSec / 3600.0)) else 0.0
            val avgPace = if (distKm > 0.05) (elapsedSec / 60.0) / distKm else 0.0
            _trackingState.update {
                it.copy(
                    avgSpeedKmh = avgSpeed,
                    avgPaceMinPerKm = avgPace
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return
        isListening = true

        // 1. Check all providers for immediate last known location
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (bestLocation != null) {
            _trackingState.update {
                it.copy(
                    isGpsActive = true,
                    currentLatitude = bestLocation.latitude,
                    currentLongitude = bestLocation.longitude,
                    currentAltitude = bestLocation.altitude,
                    accuracy = bestLocation.accuracy
                )
            }
        }

        // 2. Request live updates from both GPS and Network
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,
                        1f,
                        this,
                        Looper.getMainLooper()
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun startTracking() {
        startListening()
        lastTrackedLocation = null
        _trackingState.update { it.copy(isTracking = true) }
    }

    fun pauseTracking() {
        _trackingState.update { it.copy(isTracking = false, currentSpeedKmh = 0.0) }
    }

    fun stopListening() {
        isListening = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {}
    }

    fun reset() {
        lastTrackedLocation = null
        lastSplitElapsedSec = 0
        currentElapsedSeconds = 0
        val currentLat = _trackingState.value.currentLatitude
        val currentLon = _trackingState.value.currentLongitude
        val currentAlt = _trackingState.value.currentAltitude
        val accuracy = _trackingState.value.accuracy
        val isGps = _trackingState.value.isGpsActive

        _trackingState.value = TrainingTrackingState(
            isTracking = false,
            isGpsActive = isGps,
            currentLatitude = currentLat,
            currentLongitude = currentLon,
            currentAltitude = currentAlt,
            accuracy = accuracy
        )
    }

    override fun onLocationChanged(location: Location) {
        // Filter obviously invalid location data
        if (location.hasAccuracy() && location.accuracy > 50f) {
            return
        }

        val point = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timestamp = location.time
        )

        val isTracking = _trackingState.value.isTracking

        var newDistance = _trackingState.value.totalDistanceMeters
        var speedKmh = if (location.hasSpeed() && location.speed > 0f) {
            location.speed * 3.6 // m/s -> km/h
        } else {
            0.0
        }

        var updatedPoints = _trackingState.value.routePoints
        val currentSplits = _trackingState.value.splits.toMutableList()

        if (isTracking) {
            if (lastTrackedLocation != null) {
                val dist = lastTrackedLocation!!.distanceTo(location).toDouble()
                // Valid movement between 1m and 120m
                if (dist in 1.0..120.0) {
                    newDistance += dist

                    if (speedKmh == 0.0) {
                        val timeSec = (location.time - lastTrackedLocation!!.time) / 1000.0
                        if (timeSec > 0.5) {
                            speedKmh = (dist / timeSec) * 3.6
                        }
                    }
                }
            }
            lastTrackedLocation = location
            updatedPoints = updatedPoints + point

            // Check kilometer splits
            val currentCompletedKms = (newDistance / 1000.0).toInt()
            if (currentCompletedKms > currentSplits.size && currentCompletedKms >= 1) {
                val splitSec = currentElapsedSeconds - lastSplitElapsedSec
                lastSplitElapsedSec = currentElapsedSeconds

                val splitMin = splitSec / 60
                val splitSecRemainder = splitSec % 60
                val splitPaceStr = String.format("%02d:%02d", splitMin, splitSecRemainder)

                currentSplits.add(
                    KilometerSplit(
                        kmNumber = currentCompletedKms,
                        splitSeconds = splitSec,
                        totalElapsedSeconds = currentElapsedSeconds,
                        paceString = splitPaceStr
                    )
                )
            }
        }

        val distKm = newDistance / 1000.0
        val avgSpeed = if (currentElapsedSeconds > 0) (distKm / (currentElapsedSeconds / 3600.0)) else 0.0
        val avgPace = if (distKm > 0.05 && currentElapsedSeconds > 0) (currentElapsedSeconds / 60.0) / distKm else 0.0
        val currentPace = if (speedKmh > 0.5) (60.0 / speedKmh) else 0.0

        _trackingState.update {
            it.copy(
                isGpsActive = true,
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                currentAltitude = location.altitude,
                accuracy = location.accuracy,
                totalDistanceMeters = newDistance,
                currentSpeedKmh = speedKmh,
                avgSpeedKmh = avgSpeed,
                currentPaceMinPerKm = currentPace,
                avgPaceMinPerKm = avgPace,
                routePoints = updatedPoints,
                splits = currentSplits
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        startListening()
    }
    override fun onProviderDisabled(provider: String) {}
}
