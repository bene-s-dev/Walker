package com.benewalker.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.benewalker.app.data.WalkDao
import com.benewalker.app.data.WalkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SyncResult(
    val updatedCount: Int,
    val totalSessions: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

class HealthConnectManager(
    private val context: Context,
    private val walkDao: WalkDao
) {
    private val healthConnectClient: HealthConnectClient? by lazy {
        if (isAvailable()) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val walkingPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            walkingPermissions.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    fun createPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun syncWalkingSessions(daysBack: Int = 14): SyncResult = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext SyncResult(0, 0, errorMessage = "Health Connect ist auf diesem Gerät nicht verfügbar.")

        if (!hasPermissions()) {
            return@withContext SyncResult(0, 0, errorMessage = "Berechtigung für Trainingsdaten fehlt.")
        }

        try {
            val now = Instant.now()
            val startInstant = now.minus(daysBack.toLong(), ChronoUnit.DAYS)

            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, now),
                ascendingOrder = true
            )

            val response = client.readRecords(request)
            val zone = ZoneId.systemDefault()
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)

            // Filter strictly for Walking activities
            val walkingRecords = response.records.filter { record ->
                val isWalking = record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING ||
                        (record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT &&
                                (record.title?.contains("Geh", ignoreCase = true) == true ||
                                        record.title?.contains("Walk", ignoreCase = true) == true))
                val durationSec = Duration.between(record.startTime, record.endTime).seconds
                isWalking && durationSec >= 10
            }

            if (walkingRecords.isEmpty()) {
                return@withContext SyncResult(0, 0)
            }

            // Group by local date string (YYYY-MM-DD)
            val grouped = walkingRecords.groupBy { record ->
                dateFormatter.format(record.startTime)
            }

            var updatedCount = 0

            grouped.forEach { (dateStr, recordsForDay) ->
                val sortedSessions = recordsForDay.sortedBy { it.startTime }

                val morningSec: Int
                val eveningSec: Int

                if (sortedSessions.size == 1) {
                    morningSec = Duration.between(sortedSessions[0].startTime, sortedSessions[0].endTime).seconds.toInt()
                    eveningSec = 0
                } else {
                    // 1. Gehen
                    morningSec = Duration.between(sortedSessions[0].startTime, sortedSessions[0].endTime).seconds.toInt()
                    // 2. Gehen (und weitere Einheiten)
                    var remaining = 0
                    for (i in 1 until sortedSessions.size) {
                        remaining += Duration.between(sortedSessions[i].startTime, sortedSessions[i].endTime).seconds.toInt()
                    }
                    eveningSec = remaining
                }

                val totalSec = morningSec + eveningSec
                val existing = walkDao.getRecordByDate(dateStr)

                // Eigene Einträge (manuell, Stoppuhr, Backup) haben Vorrang vor Garmin Sync
                val isManualEntry = existing != null && existing.source != "garmin_health_connect" && existing.totalSeconds > 0

                if (!isManualEntry) {
                    if (existing == null || existing.morningSeconds != morningSec || existing.eveningSeconds != eveningSec) {
                        val recordToSave = WalkRecord(
                            date = dateStr,
                            morningSeconds = morningSec,
                            eveningSeconds = eveningSec,
                            totalSeconds = totalSec,
                            updatedAt = System.currentTimeMillis(),
                            source = "garmin_health_connect"
                        )
                        walkDao.insertOrUpdate(recordToSave)
                        updatedCount++
                    }
                }
            }

            SyncResult(
                updatedCount = updatedCount,
                totalSessions = walkingRecords.size
            )
        } catch (e: Exception) {
            SyncResult(0, 0, errorMessage = e.message ?: "Fehler beim Synchronisieren")
        }
    }
}
