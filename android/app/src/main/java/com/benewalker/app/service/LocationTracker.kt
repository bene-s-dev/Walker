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
    private var lastSmoothedLat: Double? = null
    private var lastSmoothedLon: Double? = null
    private var lastTrackedTimestamp: Long = 0L
    private var lastSplitElapsedSec: Int = 0
    private var currentElapsedSeconds: Int = 0

    companion object {
        private const val MAX_ACCURACY_METERS = 32.0f
        private const val MAX_HUMAN_SPEED_KMH = 28.0 // 7.7 m/s max for walking/sprinting
        private const val STATIONARY_DRIFT_THRESHOLD_METERS = 2.5
    }

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
                        0.5f,
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
        lastSmoothedLat = null
        lastSmoothedLon = null
        lastTrackedTimestamp = 0L
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
        lastSmoothedLat = null
        lastSmoothedLon = null
        lastTrackedTimestamp = 0L
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
        // 1. Filter inaccurate or invalid location fixes
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) {
            return
        }

        val isTracking = _trackingState.value.isTracking
        var newDistance = _trackingState.value.totalDistanceMeters
        var updatedPoints = _trackingState.value.routePoints
        val currentSplits = _trackingState.value.splits.toMutableList()

        var calculatedSpeedKmh = if (location.hasSpeed() && location.speed > 0f) {
            location.speed * 3.6 // m/s -> km/h
        } else {
            0.0
        }

        // 2. Trajectory smoothing & Road/Path estimation
        var finalLat = location.latitude
        var finalLon = location.longitude

        if (isTracking) {
            val prevLat = lastSmoothedLat
            val prevLon = lastSmoothedLon
            val prevLoc = lastTrackedLocation

            if (prevLat != null && prevLon != null && prevLoc != null) {
                val timeDeltaSec = (location.time - lastTrackedTimestamp) / 1000.0
                val rawDistanceToPrev = FloatArray(1)
                Location.distanceBetween(prevLat, prevLon, location.latitude, location.longitude, rawDistanceToPrev)
                val distMeters = rawDistanceToPrev[0].toDouble()

                // Check speed plausibility (reject multipath glitches / teleports)
                val pointSpeedKmh = if (timeDeltaSec > 0.3) (distMeters / timeDeltaSec) * 3.6 else 0.0
                if (pointSpeedKmh > MAX_HUMAN_SPEED_KMH && distMeters > 30.0) {
                    // Outlier / Glitch -> Skip this faulty reading
                    return
                }

                // Stationary filter: If distance is minimal and user is standing/slow, prevent ghost drift
                if (distMeters < STATIONARY_DRIFT_THRESHOLD_METERS && (calculatedSpeedKmh < 1.8 || pointSpeedKmh < 1.8)) {
                    calculatedSpeedKmh = 0.0
                    // Update current location indicator without accumulating false distance
                    _trackingState.update {
                        it.copy(
                            isGpsActive = true,
                            currentLatitude = location.latitude,
                            currentLongitude = location.longitude,
                            currentAltitude = location.altitude,
                            accuracy = location.accuracy,
                            currentSpeedKmh = 0.0
                        )
                    }
                    return
                }

                // Dynamic Accuracy-Weighted Smoothing (Kalman-style low-pass along paths)
                // Higher accuracy & speed -> higher responsiveness (alpha 0.85); Lower accuracy -> heavier smoothing (alpha 0.45)
                val accuracyFactor = (1.0 - (location.accuracy.coerceIn(3f, 30f) / 45.0)).coerceIn(0.45, 0.88)
                finalLat = prevLat + accuracyFactor * (location.latitude - prevLat)
                finalLon = prevLon + accuracyFactor * (location.longitude - prevLon)

                // Calculate actual distance between smoothed points along the road/path
                val smoothedDist = FloatArray(1)
                Location.distanceBetween(prevLat, prevLon, finalLat, finalLon, smoothedDist)
                val stepDistance = smoothedDist[0].toDouble()

                if (stepDistance > 0.5) {
                    newDistance += stepDistance

                    if (calculatedSpeedKmh == 0.0 && timeDeltaSec > 0.5) {
                        calculatedSpeedKmh = (stepDistance / timeDeltaSec) * 3.6
                    }
                }
            }

            lastSmoothedLat = finalLat
            lastSmoothedLon = finalLon
            lastTrackedLocation = location
            lastTrackedTimestamp = location.time

            val point = GpsPoint(
                latitude = finalLat,
                longitude = finalLon,
                altitude = location.altitude,
                timestamp = location.time
            )
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
        } else {
            // Not recording, just previewing location
            finalLat = location.latitude
            finalLon = location.longitude
        }

        val distKm = newDistance / 1000.0
        val avgSpeed = if (currentElapsedSeconds > 0) (distKm / (currentElapsedSeconds / 3600.0)) else 0.0
        val avgPace = if (distKm > 0.05 && currentElapsedSeconds > 0) (currentElapsedSeconds / 60.0) / distKm else 0.0
        val currentPace = if (calculatedSpeedKmh > 0.5) (60.0 / calculatedSpeedKmh) else 0.0

        _trackingState.update {
            it.copy(
                isGpsActive = true,
                currentLatitude = finalLat,
                currentLongitude = finalLon,
                currentAltitude = location.altitude,
                accuracy = location.accuracy,
                totalDistanceMeters = newDistance,
                currentSpeedKmh = calculatedSpeedKmh,
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
