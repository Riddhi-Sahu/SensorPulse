package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.LocalAuthRepository
import com.example.data.camera.CameraLocalFileStore
import com.example.data.local.SensorPulseDatabase
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.data.repository.TelemetryRepository
import com.example.data.sensors.DeviceSensorManager
import com.example.ui.viewmodel.SensorHubViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionAndAuthTest {

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
        val dao = db.telemetryDao()
        val authDao = db.authDao()
        repository = TelemetryRepository(dao)
        authRepository = LocalAuthRepository(context, authDao)
        authRepository.signOut()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create account sign in and sign out flow`() = runBlocking {
        // Reject invalid username formats (must be name_xxx)
        assertTrue(authRepository.signUp("RiddhSensor", "Pass@1234", "Pass@1234").isFailure)
        assertTrue(authRepository.signUp("alex_12", "Pass@1234", "Pass@1234").isFailure)
        assertTrue(authRepository.signUp("alex_1234", "Pass@1234", "Pass@1234").isFailure)
        assertTrue(authRepository.signUp("123_456", "Pass@1234", "Pass@1234").isFailure)

        // Reject invalid passwords (min 8, max 20, letter + number + special symbol)
        assertTrue(authRepository.signUp("riddh_101", "Pa@1", "Pa@1").isFailure)
        assertTrue(authRepository.signUp("riddh_101", "Pass@1234567890123456", "Pass@1234567890123456").isFailure)
        assertTrue(authRepository.signUp("riddh_101", "Password12", "Password12").isFailure)
        assertTrue(authRepository.signUp("riddh_101", "Password@#", "Password@#").isFailure)
        assertTrue(authRepository.signUp("riddh_101", "12345678@#", "12345678@#").isFailure)

        val created = authRepository.signUp("Riddh_101", "Pass@1234", "Pass@1234")
        assertTrue(created.isSuccess)
        assertEquals("Riddh_101", authRepository.currentUser.value?.username)

        val duplicate = authRepository.signUp("riddh_101", "Pass@1234", "Pass@1234")
        assertTrue(duplicate.isFailure)

        authRepository.signOut()
        assertNull(authRepository.currentUser.value)

        val wrongPw = authRepository.signIn("Riddh_101", "Wrong@123")
        assertTrue(wrongPw.isFailure)

        val signedIn = authRepository.signIn("riddh_101", "Pass@1234")
        assertTrue(signedIn.isSuccess)
        assertEquals("Riddh_101", authRepository.currentUser.value?.username)

        // Verify Room AuthDao persists signed-in state across repository instances
        val restoredRepo = LocalAuthRepository(context, db.authDao())
        restoredRepo.restoreSession()
        assertEquals("Riddh_101", restoredRepo.currentUser.value?.username)
    }

    @Test
    fun `camera capture links to active session`() = runBlocking {
        val user = authRepository.signUp("alex_104", "Pass@1234", "Pass@1234").getOrThrow()
        val sessionId = repository.startSession(
            title = "Session Test",
            userId = user.id,
            ownerUsername = user.username
        ).getOrThrow()
        val saveCapture = repository.saveCameraCapture(
            sessionId = sessionId,
            fileName = "frame_01.jpg",
            localFilePath = "/tmp/frame_01.jpg",
            lensFacing = "back",
            widthPx = 1920,
            heightPx = 1080,
            fileSizeBytes = 20480L,
            flashUsed = false,
            focalLengthMm = 4.2,
            caption = "Lab test frame"
        )
        assertTrue(saveCapture.isSuccess)

        val captures = repository.observeCameraCaptures().first()
        assertEquals(1, captures.size)
        assertEquals(sessionId, captures.first().sessionId)

        repository.stopSession(sessionId, totalSteps = 12)
        val sessions = repository.observeSessions().first()
        assertFalse(sessions.first().isActive)
        assertEquals(12, sessions.first().totalSteps)
        assertEquals(user.id, sessions.first().userId)
        assertEquals("alex_104", sessions.first().ownerUsername)

        val bundles = db.telemetryDao().observeSessionsWithTelemetry().first()
        assertEquals(1, bundles.size)
        assertEquals(1, bundles.first().captures.size)
    }

    @Test
    fun `stopping session revokes consented sensors immediately`() = runBlocking {
        val vm = SensorHubViewModel(
            repository = repository,
            sensorManager = DeviceSensorManager(context),
            cameraFileStore = CameraLocalFileStore(context)
        )
        val testSensor = SensorDescriptor(
            id = "hw_1_0",
            name = "Accelerometer",
            category = SensorCategory.MOTION,
            kind = SensorKind.HARDWARE_SENSOR,
            androidSensorType = android.hardware.Sensor.TYPE_ACCELEROMETER,
            requiredPermissions = emptyList(),
            permissionRationale = "Test rationale",
            unit = "m/s²",
            axisLabels = listOf("X", "Y", "Z"),
            vendor = "Test"
        )

        vm.openSensor(testSensor)
        vm.onPermissionGranted(testSensor)
        assertTrue(testSensor.id in vm.consentedSensorIds.value)

        vm.startRecordingSession()
        // Pump Main Looper while Room executor completes startSession
        var attempts = 0
        while (vm.activeSessionId.value == null && attempts < 100) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(20L)
            attempts++
        }
        assertNotNull(vm.activeSessionId.value)

        vm.stopRecordingSession()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(vm.activeSessionId.value)
        assertTrue(vm.consentedSensorIds.value.isEmpty())
        assertFalse(vm.isSensorStreaming.value)
        assertTrue(vm.sessionRecordingSensors.value.isEmpty())
    }
}
