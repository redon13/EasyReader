package io.github.redon13.easyreader.data.db

import androidx.room.Entity
import androidx.room.Index
import io.github.redon13.easyreader.calibre.CalibreBook

/** A book's details, copied from Calibre so browsing works instantly and offline. */
@Entity(
    tableName = "books",
    primaryKeys = ["libraryId", "id"],
    indices = [Index("libraryId", "timestamp")],
)
data class BookEntity(
    val libraryId: String,
    val id: Int,
    val title: String,
    val titleSort: String?,
    /** Authors joined with " & ", as Calibre shows them. */
    val authors: String,
    val authorSort: String?,
    val series: String?,
    val seriesIndex: Double?,
    val publisher: String?,
    val rating: Double?,
    /** Formats joined with ",", e.g. "EPUB,PDF". */
    val formats: String,
    val timestamp: String?,
    val lastModified: String?,
    val pubdate: String?,
    /** Everything Calibre sent, for later stages (book details, custom columns…). */
    val rawJson: String,
) {
    companion object {
        fun from(libraryId: String, b: CalibreBook) = BookEntity(
            libraryId = libraryId,
            id = b.id,
            title = b.title,
            titleSort = b.titleSort,
            authors = b.authors.joinToString(" & "),
            authorSort = b.authorSort,
            series = b.series,
            seriesIndex = b.seriesIndex,
            publisher = b.publisher,
            rating = b.rating,
            formats = b.formats.joinToString(","),
            timestamp = b.timestamp,
            lastModified = b.lastModified,
            pubdate = b.pubdate,
            rawJson = b.rawJson,
        )
    }
}
