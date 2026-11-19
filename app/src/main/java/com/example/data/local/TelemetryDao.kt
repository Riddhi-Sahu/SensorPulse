package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

    @Query("SELECT * FROM recording_sessions ORDER BY startedAtMs DESC")
    fun observeSessions(): Flow<List<RecordingSessionEntity>>

    @Transaction
    @Query("SELECT * FROM recording_sessions ORDER BY startedAtMs DESC")
    fun observeSessionsWithTelemetry(): Flow<List<SessionWithTelemetry>>

    @Transaction
    @Query("SELECT * FROM recording_sessions ORDER BY startedAtMs DESC")
    suspend fun getAllSessionsWithTelemetry(): List<SessionWithTelemetry>

    @Transaction
    @Query("SELECT * FROM recording_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionWithTelemetryById(sessionId: Long): SessionWithTelemetry?

    @Query("SELECT * FROM recording_sessions WHERE userId = :userId ORDER BY startedAtMs DESC")
    fun observeSessionsForUser(userId: Long): Flow<List<RecordingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: RecordingSessionEntity): Long

    @Query("UPDATE recording_sessions SET endedAtMs = :endedAtMs, isActive = 0, totalSteps = :totalSteps WHERE id = :sessionId")
    suspend fun finishSession(sessionId: Long, endedAtMs: Long, totalSteps: Int)

    @Query("UPDATE recording_sessions SET totalSteps = :totalSteps WHERE id = :sessionId")
    suspend fun updateSessionSteps(sessionId: Long, totalSteps: Int)

    @Query("DELETE FROM recording_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM sensor_readings WHERE sessionId = :sessionId")
    suspend fun deleteReadingsForSession(sessionId: Long)

    @Query("UPDATE camera_captures SET sessionId = NULL WHERE sessionId = :sessionId")
    suspend fun clearSessionIdForCaptures(sessionId: Long)

    @Query("SELECT * FROM sensor_readings ORDER BY createdAtMs DESC")
    fun observeSensorReadings(): Flow<List<SensorReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorReading(entity: SensorReadingEntity): Long

    @Query("UPDATE sensor_readings SET notes = :notes, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateReadingNotes(id: Long, notes: String?, updatedAtMs: Long)

    @Query("DELETE FROM sensor_readings WHERE id = :id")
    suspend fun deleteSensorReading(id: Long)

    @Query("SELECT * FROM camera_captures ORDER BY createdAtMs DESC")
    fun observeCameraCaptures(): Flow<List<CameraCaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCameraCapture(entity: CameraCaptureEntity): Long

    @Query("UPDATE camera_captures SET caption = :caption, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateCaptureCaption(id: Long, caption: String?, updatedAtMs: Long)

    @Query("DELETE FROM camera_captures WHERE id = :id")
    suspend fun deleteCameraCapture(id: Long)
}
