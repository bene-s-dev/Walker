package com.benewalker.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isResumePoint: Boolean = false
)

fun splitRouteSegments(points: List<GpsPoint>): List<List<GpsPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<GpsPoint>>()
    var currentSegment = mutableListOf<GpsPoint>()

    for (i in points.indices) {
        val p = points[i]
        val isGap = p.isResumePoint || (i > 0 && (p.timestamp - points[i - 1].timestamp) > 25_000L)
        if (isGap && currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
            currentSegment = mutableListOf()
        }
        currentSegment.add(p)
    }
    if (currentSegment.isNotEmpty()) {
        segments.add(currentSegment)
    }
    return segments
}

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

class LocationTracker private constructor(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _trackingState = MutableStateFlow(TrainingTrackingState())
    val trackingState: StateFlow<TrainingTrackingState> = _trackingState.asStateFlow()

    private var isListening = false
    private var isResumePending = false
    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null
    private var lastLocationTimestampRealtime: Long = 0L
    private var lastSplitElapsedSec: Int = 0
    private var currentElapsedSeconds: Int = 0

    // Callback when a new kilometer split is reached (for TTS in Foreground Service)
    var onSplitReached: ((KilometerSplit) -> Unit)? = null

    companion object {
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 28.0f
        private const val MAX_PLAUSIBLE_SPEED_KMH = 35.0 // ~9.7 m/s max for human foot tracking
        private const val JITTER_ACCURACY_RATIO = 0.25 // stationary jitter threshold

        @Volatile
        private var INSTANCE: LocationTracker? = null

        fun getInstance(context: Context): LocationTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun updateElapsedSeconds(elapsedSec: Int) {
        currentElapsedSeconds = elapsedSec
        val currentDist = _trackingState.value.totalDistanceMeters
        if (currentDist > 0) {
            val distKm = currentDist / 1000.0
            val avgSpeed = if (elapsedSec > 0) (distKm / (elapsedSec / 3600.0)) else 0.0
            val avgPace = if (distKm > 0.02 && elapsedSec > 0) (elapsedSec / 60.0) / distKm else 0.0
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

        // 1. Initial best last known location (only if recent < 30s)
        var bestLocation: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val now = System.currentTimeMillis()
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (now - loc.time < 30_000L)) {
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

        // 2. Request updates: Prioritize GPS_PROVIDER for precise tracking
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this,
                    Looper.getMainLooper()
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                // Fallback only if GPS hardware is disabled
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    this,
                    Looper.getMainLooper()
                )
            }
        } catch (_: Exception) {}
    }

    fun startTracking() {
        if (_trackingState.value.routePoints.isNotEmpty()) {
            isResumePending = true
        }
        lastRecordedLat = null
        lastRecordedLon = null
        lastLocationTimestampRealtime = 0L
        _trackingState.update { it.copy(isTracking = true) }
        startListening()
    }

    fun pauseTracking() {
        stopListening()
        lastRecordedLat = null
        lastRecordedLon = null
        lastLocationTimestampRealtime = 0L
        if (_trackingState.value.routePoints.isNotEmpty()) {
            isResumePending = true
        }
        _trackingState.update {
            it.copy(
                isTracking = false,
                isGpsActive = false,
                currentSpeedKmh = 0.0,
                currentPaceMinPerKm = 0.0
            )
        }
    }

    fun stopListening() {
        isListening = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {}
    }

    fun reset() {
        stopListening()
        isResumePending = false
        lastRecordedLat = null
        lastRecordedLon = null
        lastLocationTimestampRealtime = 0L
        lastSplitElapsedSec = 0
        currentElapsedSeconds = 0
        val currentLat = _trackingState.value.currentLatitude
        val currentLon = _trackingState.value.currentLongitude
        val currentAlt = _trackingState.value.currentAltitude
        val accuracy = _trackingState.value.accuracy

        _trackingState.value = TrainingTrackingState(
            isTracking = false,
            isGpsActive = false,
            currentLatitude = currentLat,
            currentLongitude = currentLon,
            currentAltitude = currentAlt,
            accuracy = accuracy
        )
    }

    override fun onLocationChanged(location: Location) {
        // 1. Basic sanity filter
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            // Still update accuracy state for UI indication, but skip trajectory calculation
            _trackingState.update {
                it.copy(
                    isGpsActive = true,
                    currentLatitude = location.latitude,
                    currentLongitude = location.longitude,
                    currentAltitude = location.altitude,
                    accuracy = location.accuracy
                )
            }
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val isTracking = _trackingState.value.isTracking
        var totalDist = _trackingState.value.totalDistanceMeters
        var points = _trackingState.value.routePoints
        val splits = _trackingState.value.splits.toMutableList()

        var calculatedSpeedKmh = if (location.hasSpeed() && location.speed > 0f) {
            location.speed * 3.6 // m/s -> km/h
        } else {
            0.0
        }

        var smoothedLat = location.latitude
        var smoothedLon = location.longitude

        if (isTracking) {
            val isResume = isResumePending
            val prevLat = if (isResume) null else lastRecordedLat
            val prevLon = if (isResume) null else lastRecordedLon
            val prevTime = if (isResume) 0L else lastLocationTimestampRealtime

            if (prevLat != null && prevLon != null && prevTime > 0) {
                val timeDeltaSec = (nowRealtime - prevTime) / 1000.0
                val results = FloatArray(1)
                Location.distanceBetween(prevLat, prevLon, location.latitude, location.longitude, results)
                val distMeters = results[0].toDouble()

                // Check speed plausibility (reject multi-path teleports)
                val impliedSpeedKmh = if (timeDeltaSec > 0.2) (distMeters / timeDeltaSec) * 3.6 else 0.0
                if (impliedSpeedKmh > MAX_PLAUSIBLE_SPEED_KMH && distMeters > 30.0) {
                    // Glitch point -> ignore
                    return
                }

                // Stationary jitter filter:
                // If user is stationary, small GPS oscillations around 0.5-1.5m shouldn't falsely inflate distance
                val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
                val isStationaryJitter = distMeters < 0.6 && calculatedSpeedKmh < 0.5 && impliedSpeedKmh < 0.8

                if (!isStationaryJitter) {
                    // Smooth point slightly based on accuracy
                    val alpha = (1.0 - (accuracy.coerceIn(2f, 25f) / 50.0)).coerceIn(0.65, 0.95)
                    smoothedLat = prevLat + alpha * (location.latitude - prevLat)
                    smoothedLon = prevLon + alpha * (location.longitude - prevLon)

                    val smoothResults = FloatArray(1)
                    Location.distanceBetween(prevLat, prevLon, smoothedLat, smoothedLon, smoothResults)
                    val stepDistance = smoothResults[0].toDouble()

                    totalDist += stepDistance

                    if (calculatedSpeedKmh == 0.0 && timeDeltaSec > 0.5) {
                        calculatedSpeedKmh = (stepDistance / timeDeltaSec) * 3.6
                    }
                } else {
                    calculatedSpeedKmh = 0.0
                }
            } else {
                // First point or resume point: do NOT connect or accumulate distance across the pause gap!
                if (calculatedSpeedKmh == 0.0 && location.hasSpeed() && location.speed > 0f) {
                    calculatedSpeedKmh = location.speed * 3.6
                }
            }

            lastRecordedLat = smoothedLat
            lastRecordedLon = smoothedLon
            lastLocationTimestampRealtime = nowRealtime

            val point = GpsPoint(
                latitude = smoothedLat,
                longitude = smoothedLon,
                altitude = location.altitude,
                timestamp = System.currentTimeMillis(),
                isResumePoint = isResume
            )
            points = points + point
            if (isResume) {
                isResumePending = false
            }

            // Check kilometer splits
            val completedKms = (totalDist / 1000.0).toInt()
            if (completedKms > splits.size && completedKms >= 1) {
                val splitSec = currentElapsedSeconds - lastSplitElapsedSec
                lastSplitElapsedSec = currentElapsedSeconds

                val splitMin = splitSec / 60
                val splitSecRemainder = splitSec % 60
                val splitPaceStr = String.format(Locale.GERMAN, "%02d:%02d", splitMin, splitSecRemainder)

                val newSplit = KilometerSplit(
                    kmNumber = completedKms,
                    splitSeconds = splitSec,
                    totalElapsedSeconds = currentElapsedSeconds,
                    paceString = splitPaceStr
                )
                splits.add(newSplit)
                try {
                    onSplitReached?.invoke(newSplit)
                } catch (_: Exception) {}
            }
        }

        val distKm = totalDist / 1000.0
        val avgSpeed = if (currentElapsedSeconds > 0) (distKm / (currentElapsedSeconds / 3600.0)) else 0.0
        val avgPace = if (distKm > 0.02 && currentElapsedSeconds > 0) (currentElapsedSeconds / 60.0) / distKm else 0.0
        val currentPace = if (calculatedSpeedKmh > 0.3) (60.0 / calculatedSpeedKmh) else 0.0

        _trackingState.update {
            it.copy(
                isGpsActive = true,
                currentLatitude = smoothedLat,
                currentLongitude = smoothedLon,
                currentAltitude = location.altitude,
                accuracy = location.accuracy,
                totalDistanceMeters = totalDist,
                currentSpeedKmh = calculatedSpeedKmh,
                avgSpeedKmh = avgSpeed,
                currentPaceMinPerKm = currentPace,
                avgPaceMinPerKm = avgPace,
                routePoints = points,
                splits = splits
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        startListening()
    }
    override fun onProviderDisabled(provider: String) {}
}
