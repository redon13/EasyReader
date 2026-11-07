package io.github.redon13.easyreader.calibre

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * The owner's place in a book, as kept in Calibre's #er_position column.
 *
 * Stored as JSON, for example:
 * {"locator": {…}, "progress": 42.5, "timestamp": "2026-11-07T21:16:08Z",
 *  "device": "Galaxy Tab S9 FE", "format": "EPUB"}
 */
data class ReadingPosition(
    /** Readium's exact place in the book, as JSON text. */
    val locator: String?,
    /** How far through the book, 0–1. */
    val progress: Double,
    /** When it was saved, in milliseconds since 1970 (UTC). */
    val timestamp: Long,
    /** Which device saved it. */
    val device: String?,
    /** Which format was being read, e.g. "EPUB". */
    val format: String?,
) {
    fun toJson(): String = buildJsonObject {
        locator?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }?.let { put("locator", it) }
        put("progress", Math.round(progress.coerceIn(0.0, 1.0) * 1000) / 10.0)
        put("timestamp", Instant.ofEpochMilli(timestamp).toString())
        device?.let { put("device", it) }
        format?.let { put("format", it) }
    }.toString()

    /** True if this was saved more recently than [other]. */
    fun isNewerThan(other: ReadingPosition?): Boolean = other == null || timestamp > other.timestamp

    companion object {
        const val COLUMN = "#er_position"

        /** Reads the column's text; null if it's empty or not EasyReader's format. */
        fun parse(text: String?): ReadingPosition? {
            if (text.isNullOrBlank()) return null
            return try {
                val o = Json.parseToJsonElement(text.trim()).jsonObject
                ReadingPosition(
                    locator = (o["locator"] as? JsonObject)?.toString(),
                    progress = ((o["progress"] as? JsonPrimitive)?.doubleOrNull ?: 0.0).div(100).coerceIn(0.0, 1.0),
                    timestamp = (o["timestamp"] as? JsonPrimitive)?.contentOrNull
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                    device = (o["device"] as? JsonPrimitive)?.contentOrNull,
                    format = (o["format"] as? JsonPrimitive)?.contentOrNull,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** This book's reading position as last synced from Calibre, if any. */
val CalibreBook.serverPosition: ReadingPosition?
    get() = ReadingPosition.parse(customColumn(ReadingPosition.COLUMN)?.values?.firstOrNull())
