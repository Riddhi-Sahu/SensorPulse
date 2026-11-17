package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.SensorReading
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TelemetryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthenticatedUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        }
        return uid
    }

    fun observeSensorReadings(userId: String): Flow<List<SensorReading>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/sensor_readings"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("sensor_readings")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SensorReading::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCameraCaptures(userId: String): Flow<List<CameraCaptureRecord>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/camera_captures"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("camera_captures")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CameraCaptureRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveSensorReading(
        userId: String,
        sensorId: String,
        sensorName: String,
        sensorCategory: String,
        values: List<Double>,
        unit: String,
        accuracy: Int,
        notes: String? = null
    ): Result<Unit> {
        var docPath = "users/$userId/sensor_readings"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            val safeValues = values.ifEmpty { listOf(0.0) }.take(16)
            require(sensorId.isNotBlank() && sensorId.length <= 128) { "Invalid sensorId" }
            require(sensorName.isNotBlank() && sensorName.length <= 120) { "Invalid sensorName" }

            val docRef = db.collection("users")
                .document(uid)
                .collection("sensor_readings")
                .document()
            docPath = docRef.path

            val record = SensorReading(
                userId = uid,
                sensorId = sensorId,
                sensorName = sensorName,
                sensorCategory = sensorCategory,
                values = safeValues,
                unit = unit,
                accuracy = accuracy.coerceIn(0, 5),
                notes = notes?.trim()?.takeIf { it.isNotEmpty() }
            )
            docRef.set(record.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCameraCapture(
        userId: String,
        fileName: String,
        localFilePath: String,
        lensFacing: String,
        widthPx: Int,
        heightPx: Int,
        fileSizeBytes: Long,
        flashUsed: Boolean,
        focalLengthMm: Double? = null,
        caption: String? = null
    ): Result<Unit> {
        var docPath = "users/$userId/camera_captures"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(fileName.isNotBlank() && fileName.length <= 128) { "Invalid fileName" }
            require(localFilePath.isNotBlank() && localFilePath.length <= 512) { "Invalid localFilePath" }
            require(widthPx >= 1 && heightPx >= 1) { "Invalid image dimensions" }
            require(fileSizeBytes >= 1L) { "Invalid file size" }

            val docRef = db.collection("users")
                .document(uid)
                .collection("camera_captures")
                .document()
            docPath = docRef.path

            val record = CameraCaptureRecord(
                userId = uid,
                fileName = fileName,
                localFilePath = localFilePath,
                lensFacing = if (lensFacing == "front") "front" else "back",
                widthPx = widthPx,
                heightPx = heightPx,
                fileSizeBytes = fileSizeBytes,
                flashUsed = flashUsed,
                focalLengthMm = focalLengthMm,
                caption = caption?.trim()?.takeIf { it.isNotEmpty() }
            )
            docRef.set(record.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReadingNotes(
        userId: String,
        readingId: String,
        notes: String
    ): Result<Unit> {
        var docPath = "users/$userId/sensor_readings/$readingId"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(readingId.isNotBlank()) { "Reading ID must not be blank" }
            val trimmed = notes.trim().take(300)
            val docRef = db.collection("users")
                .document(uid)
                .collection("sensor_readings")
                .document(readingId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "notes" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCaptureCaption(
        userId: String,
        captureId: String,
        caption: String
    ): Result<Unit> {
        var docPath = "users/$userId/camera_captures/$captureId"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(captureId.isNotBlank()) { "Capture ID must not be blank" }
            val trimmed = caption.trim().take(240)
            val docRef = db.collection("users")
                .document(uid)
                .collection("camera_captures")
                .document(captureId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "caption" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSensorReading(userId: String, readingId: String): Result<Unit> {
        var docPath = "users/$userId/sensor_readings/$readingId"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(readingId.isNotBlank()) { "Reading ID must not be blank" }
            val docRef = db.collection("users")
                .document(uid)
                .collection("sensor_readings")
                .document(readingId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCameraCapture(
        userId: String,
        captureId: String,
        localFilePath: String?
    ): Result<Unit> {
        var docPath = "users/$userId/camera_captures/$captureId"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(captureId.isNotBlank()) { "Capture ID must not be blank" }
            val docRef = db.collection("users")
                .document(uid)
                .collection("camera_captures")
                .document(captureId)
            docPath = docRef.path
            docRef.delete().await()
            if (!localFilePath.isNullOrBlank()) {
                runCatching {
                    val f = File(localFilePath)
                    if (f.exists()) f.delete()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
