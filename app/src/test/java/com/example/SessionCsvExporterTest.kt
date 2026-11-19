package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.LocalAuthRepository
import com.example.data.export.SessionCsvExporter
import com.example.data.local.SensorPulseDatabase
import com.example.data.repository.TelemetryRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionCsvExporterTest {

    private lateinit var context: Context
    private lateinit var db: SensorPulseDatabase
    private lateinit var repository: TelemetryRepository
    private lateinit var authRepository: LocalAuthRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SensorPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TelemetryRepository(db.telemetryDao())
        authRepository = LocalAuthRepository(context, db.authDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `export single and all Room sessions produces valid RFC 4180 CSV`() = runBlocking {
        val user = authRepository.signUp("alex_104", "Pass@1234", "Pass@1234").getOrThrow()

        // Session 1 with a sensor reading (containing commas/quotes in notes) and a camera capture
        val session1 = repository.startSession(
            title = "Lab Walk, Bench \"A\"",
            userId = user.id,
            ownerUsername = user.username
        ).getOrThrow()

        repository.saveSensorReading(
            sessionId = session1,
            sensorId = "hw_1_0",
            sensorName = "Accelerometer",
            sensorCategory = "motion",
            values = listOf(0.125, -0.5, 9.81),
            unit = "m/s²",
            accuracy = 3,
            stepCount = 14,
            notes = "Measured near \"door\", line 1\nline 2"
        ).getOrThrow()

        repository.saveCameraCapture(
            sessionId = session1,
            fileName = "capture_01.jpg",
            localFilePath = "/tmp/capture_01.jpg",
            lensFacing = "back",
            widthPx = 1920,
            heightPx = 1080,
            fileSizeBytes = 40960L,
            flashUsed = true,
            focalLengthMm = 4.25,
            caption = "Desk frame, calibrated"
        ).getOrThrow()

        repository.stopSession(session1, totalSteps = 14).getOrThrow()

        // Session 2 with zero readings/captures -> emits SESSION_SUMMARY row
        val session2 = repository.startSession(
            title = "Empty Probe",
            userId = user.id,
            ownerUsername = user.username
        ).getOrThrow()

        val allOut = ByteArrayOutputStream()
        val allResult = repository.exportSessionsCsv(allOut).getOrThrow()
        assertEquals(2, allResult.sessionCount)
        assertEquals(3, allResult.rowCount)

        val csv = allOut.toString(Charsets.UTF_8.name())
        val firstLine = csv.lineSequence().first()
        assertEquals(SessionCsvExporter.COLUMNS.joinToString(","), firstLine)
        assertFalse(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Lab Walk, Bench \"\"A\"\"\""))
        assertTrue(csv.contains("\"Measured near \"\"door\"\", line 1\nline 2\""))
        assertTrue(csv.contains("SENSOR_READING"))
        assertTrue(csv.contains("CAMERA_CAPTURE"))
        assertTrue(csv.contains("SESSION_SUMMARY"))
        assertTrue(csv.contains("0.125,-0.5,9.81"))
        assertTrue(csv.contains("capture_01.jpg,back,1920x1080,4.25,true"))

        // Single-session export isolates only Session 1
        val singleOut = ByteArrayOutputStream()
        val singleResult = repository.exportSessionsCsv(singleOut, sessionId = session1).getOrThrow()
        assertEquals(1, singleResult.sessionCount)
        assertEquals(2, singleResult.rowCount)
        val singleCsv = singleOut.toString(Charsets.UTF_8.name())
        assertFalse(singleCsv.contains("Empty Probe"))
        assertTrue(singleCsv.contains("alex_104"))
    }

    @Test
    fun `export fails cleanly when no sessions exist in Room`() = runBlocking {
        val out = ByteArrayOutputStream()
        val result = repository.exportSessionsCsv(out)
        assertTrue(result.isFailure)
        assertEquals(0, out.size())
    }
}
