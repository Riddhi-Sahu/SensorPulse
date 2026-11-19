package com.example.data.export

import com.example.data.local.CameraCaptureEntity
import com.example.data.local.SensorReadingEntity
import com.example.data.local.SessionWithTelemetry
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CsvExportSummary(
    val sessionCount: Int,
    val rowCount: Int,
    val csvContent: String
)

object SessionCsvExporter {

    val COLUMNS: List<String> = listOf(
        "session_id",
        "session_title",
        "operator_username",
        "session_started_ms",
        "session_started_iso",
        "session_ended_ms",
        "session_status",
        "session_total_steps",
        "record_type",
        "record_id",
        "recorded_at_ms",
        "recorded_at_iso",
        "sensor_id",
        "sensor_name",
        "sensor_category",
        "unit",
        "accuracy",
        "step_count",
        "v0",
        "v1",
        "v2",
        "v3",
        "v4",
        "v5",
        "values_all",
        "notes_or_caption",
        "camera_file_name",
        "camera_lens",
        "camera_resolution",
        "camera_focal_mm",
        "camera_flash"
    )

    private const val CHANNEL_COLUMN_COUNT = 6

    fun buildCsv(sessions: List<SessionWithTelemetry>): CsvExportSummary {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fun formatIso(epochMs: Long): String = isoFormatter.format(Date(epochMs))

        val sb = StringBuilder()
        sb.append(COLUMNS.joinToString(",") { escapeCsv(it) }).append('\n')

        var dataRowCount = 0

        for (bundle in sessions) {
            val session = bundle.session
            val sessionStatus = if (session.isActive) "ACTIVE" else "COMPLETED"
            val sessionStartedIso = formatIso(session.startedAtMs)
            val sessionEndedStr = session.endedAtMs?.toString().orEmpty()

            val sortedReadings = bundle.readings.sortedWith(
                compareBy<SensorReadingEntity> { it.createdAtMs }.thenBy { it.id }
            )
            val sortedCaptures = bundle.captures.sortedWith(
                compareBy<CameraCaptureEntity> { it.createdAtMs }.thenBy { it.id }
            )

            if (sortedReadings.isEmpty() && sortedCaptures.isEmpty()) {
                val row = listOf(
                    session.id.toString(),
                    session.title,
                    session.ownerUsername.orEmpty(),
                    session.startedAtMs.toString(),
                    sessionStartedIso,
                    sessionEndedStr,
                    sessionStatus,
                    session.totalSteps.toString(),
                    "SESSION_SUMMARY",
                    session.id.toString(),
                    session.startedAtMs.toString(),
                    sessionStartedIso,
                    "",
                    "",
                    "",
                    "",
                    "",
                    session.totalSteps.toString(),
                    "", "", "", "", "", "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
                )
                sb.append(row.joinToString(",") { escapeCsv(it) }).append('\n')
                dataRowCount++
                continue
            }

            val timeline = buildList {
                sortedReadings.forEach { add(TimelineEntry.Reading(it.createdAtMs, it)) }
                sortedCaptures.forEach { add(TimelineEntry.Capture(it.createdAtMs, it)) }
            }.sortedBy { it.timestampMs }

            for (entry in timeline) {
                val row = when (entry) {
                    is TimelineEntry.Reading -> {
                        val r = entry.entity
                        val channelCols = (0 until CHANNEL_COLUMN_COUNT).map { idx ->
                            r.values.getOrNull(idx)?.let { formatDouble(it) }.orEmpty()
                        }
                        val allValuesStr = r.values.joinToString(";") { formatDouble(it) }
                        listOf(
                            session.id.toString(),
                            session.title,
                            session.ownerUsername.orEmpty(),
                            session.startedAtMs.toString(),
                            sessionStartedIso,
                            sessionEndedStr,
                            sessionStatus,
                            session.totalSteps.toString(),
                            "SENSOR_READING",
                            r.id.toString(),
                            r.createdAtMs.toString(),
                            formatIso(r.createdAtMs),
                            r.sensorId,
                            r.sensorName,
                            r.sensorCategory,
                            r.unit,
                            r.accuracy.toString(),
                            r.stepCount.toString(),
                            channelCols[0],
                            channelCols[1],
                            channelCols[2],
                            channelCols[3],
                            channelCols[4],
                            channelCols[5],
                            allValuesStr,
                            r.notes.orEmpty(),
                            "",
                            "",
                            "",
                            "",
                            ""
                        )
                    }

                    is TimelineEntry.Capture -> {
                        val c = entry.entity
                        listOf(
                            session.id.toString(),
                            session.title,
                            session.ownerUsername.orEmpty(),
                            session.startedAtMs.toString(),
                            sessionStartedIso,
                            sessionEndedStr,
                            sessionStatus,
                            session.totalSteps.toString(),
                            "CAMERA_CAPTURE",
                            c.id.toString(),
                            c.createdAtMs.toString(),
                            formatIso(c.createdAtMs),
                            "camera_main",
                            "Camera (${c.lensFacing})",
                            "camera",
                            "frame",
                            "",
                            "",
                            "", "", "", "", "", "",
                            "",
                            c.caption.orEmpty(),
                            c.fileName,
                            c.lensFacing,
                            "${c.widthPx}x${c.heightPx}",
                            c.focalLengthMm?.let { formatDouble(it) }.orEmpty(),
                            c.flashUsed.toString()
                        )
                    }
                }
                sb.append(row.joinToString(",") { escapeCsv(it) }).append('\n')
                dataRowCount++
            }
        }

        return CsvExportSummary(
            sessionCount = sessions.size,
            rowCount = dataRowCount,
            csvContent = sb.toString()
        )
    }

    fun writeToStream(
        outputStream: OutputStream,
        sessions: List<SessionWithTelemetry>
    ): CsvExportSummary {
        val summary = buildCsv(sessions)
        val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
        writer.write(summary.csvContent)
        writer.flush()
        return summary
    }

    fun escapeCsv(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun formatDouble(value: Double): String {
        return if (value.isNaN() || value.isInfinite()) {
            ""
        } else {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }

    private sealed interface TimelineEntry {
        val timestampMs: Long

        data class Reading(
            override val timestampMs: Long,
            val entity: SensorReadingEntity
        ) : TimelineEntry

        data class Capture(
            override val timestampMs: Long,
            val entity: CameraCaptureEntity
        ) : TimelineEntry
    }
}
