package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

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
