package com.benewalker.app

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@CapacitorPlugin(name = "HealthConnect")
class HealthConnectPlugin : Plugin() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var healthConnectClient: HealthConnectClient? = null
    private var permissionLauncher: ActivityResultLauncher<Set<String>>? = null
    private var pendingPermissionCall: PluginCall? = null

    private val walkingPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    override fun load() {
        super.load()
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
        }

        // Register permission request contract
        val contract = PermissionController.createRequestPermissionResultContract()
        permissionLauncher = activity.registerForActivityResult(contract) { grantedPermissions ->
            val call = pendingPermissionCall
            pendingPermissionCall = null
            if (call != null) {
                val hasAll = walkingPermissions.all { it in grantedPermissions }
                val ret = JSObject()
                ret.put("granted", hasAll)
                val grantedArray = JSArray()
                grantedPermissions.forEach { grantedArray.put(it) }
                ret.put("grantedPermissions", grantedArray)
                call.resolve(ret)
            }
        }
    }

    private fun getClient(): HealthConnectClient? {
        if (healthConnectClient == null && HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
        }
        return healthConnectClient
    }

    @PluginMethod
    fun checkAvailability(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        val ret = JSObject()
        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> {
                ret.put("available", true)
                ret.put("status", "AVAILABLE")
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                ret.put("available", false)
                ret.put("status", "UPDATE_REQUIRED")
            }
            else -> {
                ret.put("available", false)
                ret.put("status", "UNAVAILABLE")
            }
        }
        call.resolve(ret)
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val client = getClient()
        if (client == null) {
            val ret = JSObject()
            ret.put("granted", false)
            ret.put("error", "Health Connect not available")
            call.resolve(ret)
            return
        }

        scope.launch {
            try {
                val granted = withContext(Dispatchers.IO) {
                    client.permissionController.getGrantedPermissions()
                }
                val hasAll = walkingPermissions.all { it in granted }
                val ret = JSObject()
                ret.put("granted", hasAll)
                val grantedArray = JSArray()
                granted.forEach { grantedArray.put(it) }
                ret.put("grantedPermissions", grantedArray)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to check permissions: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        val client = getClient()
        if (client == null) {
            call.reject("Health Connect is not available on this device")
            return
        }

        pendingPermissionCall = call
        try {
            permissionLauncher?.launch(walkingPermissions) ?: run {
                call.reject("Permission launcher not initialized")
            }
        } catch (e: Exception) {
            pendingPermissionCall = null
            call.reject("Failed to launch Health Connect permission request: ${e.message}", e)
        }
    }

    @PluginMethod
    fun getWalkingSessions(call: PluginCall) {
        val client = getClient()
        if (client == null) {
            call.reject("Health Connect is not available on this device")
            return
        }

        val daysBack = call.getInt("days", 7) ?: 7
        val startDateStr = call.getString("startDate")
        val endDateStr = call.getString("endDate")

        val endInstant = if (endDateStr != null) {
            try { Instant.parse(endDateStr) } catch (_: Exception) { Instant.now() }
        } else {
            Instant.now()
        }

        val startInstant = if (startDateStr != null) {
            try { Instant.parse(startDateStr) } catch (_: Exception) { endInstant.minus(daysBack.toLong(), ChronoUnit.DAYS) }
        } else {
            endInstant.minus(daysBack.toLong(), ChronoUnit.DAYS)
        }

        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                        ascendingOrder = true
                    )
                    client.readRecords(request)
                }

                val zone = ZoneId.systemDefault()
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

                val sessionsArray = JSArray()

                for (record in response.records) {
                    // Filter exclusively for Walking (EXERCISE_TYPE_WALKING = 79)
                    val isWalking = record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING ||
                            record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT && (record.title?.contains("Geh", ignoreCase = true) == true || record.title?.contains("Walk", ignoreCase = true) == true)

                    if (!isWalking) {
                        continue
                    }

                    val duration = Duration.between(record.startTime, record.endTime)
                    val durationSeconds = duration.seconds

                    // Ignore trivial accidental sessions (< 10 seconds)
                    if (durationSeconds < 10) {
                        continue
                    }

                    val dateStr = dateFormatter.format(record.startTime)
                    val startTimeFormatted = timeFormatter.format(record.startTime)
                    val endTimeFormatted = timeFormatter.format(record.endTime)

                    val item = JSObject()
                    item.put("id", record.metadata.id)
                    item.put("date", dateStr)
                    item.put("startTime", record.startTime.toString())
                    item.put("endTime", record.endTime.toString())
                    item.put("startTimeFormatted", startTimeFormatted)
                    item.put("endTimeFormatted", endTimeFormatted)
                    item.put("durationSeconds", durationSeconds)
                    item.put("durationMinutes", (durationSeconds / 60.0))
                    item.put("title", record.title ?: "Gehen")
                    item.put("notes", record.notes ?: "")
                    item.put("sourceApp", record.metadata.dataOrigin.packageName)
                    item.put("exerciseType", "walking")

                    sessionsArray.put(item)
                }

                val result = JSObject()
                result.put("sessions", sessionsArray)
                result.put("count", sessionsArray.length())
                call.resolve(result)

            } catch (e: Exception) {
                call.reject("Failed to read walking sessions: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun openHealthConnectSettings(call: PluginCall) {
        try {
            val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            val ret = JSObject()
            ret.put("success", true)
            call.resolve(ret)
        } catch (e: Exception) {
            // Fallback to Play Store or general settings
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e2: Exception) {
                call.reject("Could not open Health Connect settings: ${e2.message}")
            }
        }
    }
}
