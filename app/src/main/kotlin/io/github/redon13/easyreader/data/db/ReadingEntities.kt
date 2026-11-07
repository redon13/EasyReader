package io.github.redon13.easyreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The latest known reading position for a Calibre book: either saved on this
 * device or fetched from Calibre, whichever is newer.
 */
@Entity(tableName = "positions", primaryKeys = ["libraryId", "bookId"])
data class PositionEntity(
    val libraryId: String,
    val bookId: Int,
    /** [io.github.redon13.easyreader.calibre.ReadingPosition] as JSON text. */
    val json: String,
    val timestamp: Long,
    /** True until it has been sent to Calibre. */
    val pending: Boolean,
)

/** A Calibre book file kept on the device for offline reading. */
@Entity(tableName = "downloads", primaryKeys = ["libraryId", "bookId"])
data class DownloadEntity(
    val libraryId: String,
    val bookId: Int,
    val format: String,
    val path: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
)

/** A book opened from the device's own files (not from Calibre). Its place is kept on this device only. */
@Entity(tableName = "device_books")
data class DeviceBookEntity(
    /** A fingerprint of the file's contents, so the same book is recognised again. */
    @PrimaryKey val hash: String,
    val title: String,
    val path: String,
    val format: String,
    val positionJson: String?,
    val openedAt: Long,
)
