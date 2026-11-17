package com.example.data.camera

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LocalCaptureFileInfo(
    val file: File,
    val fileName: String,
    val absolutePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long
)

class CameraLocalFileStore(context: Context) {
    private val capturesDir: File = File(context.applicationContext.filesDir, "camera_captures").apply {
        if (!exists()) mkdirs()
    }

    fun createCaptureDestinationFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortUid = UUID.randomUUID().toString().take(6)
        val fileName = "SP_${stamp}_${shortUid}.jpg"
        return File(capturesDir, fileName)
    }

    fun inspectSavedFile(file: File): LocalCaptureFileInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth.coerceAtLeast(1)
        val height = options.outHeight.coerceAtLeast(1)
        val bytes = file.length().coerceAtLeast(1L)
        return LocalCaptureFileInfo(
            file = file,
            fileName = file.name,
            absolutePath = file.absolutePath,
            widthPx = width,
            heightPx = height,
            fileSizeBytes = bytes
        )
    }
}
