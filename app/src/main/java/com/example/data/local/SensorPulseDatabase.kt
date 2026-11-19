package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LocalUserEntity::class,
        RecordingSessionEntity::class,
        SensorReadingEntity::class,
        CameraCaptureEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(TelemetryConverters::class)
abstract class SensorPulseDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: SensorPulseDatabase? = null

        fun getInstance(context: Context): SensorPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SensorPulseDatabase::class.java,
                    "sensorpulse_local.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
