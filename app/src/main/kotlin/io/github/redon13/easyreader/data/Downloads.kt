package io.github.redon13.easyreader.data

import android.content.Context
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.data.db.DownloadEntity
import io.github.redon13.easyreader.data.db.ReadingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Book files kept on the device so they can be read offline. */
class Downloads(context: Context, private val dao: ReadingDao) {
    private val root = File(context.filesDir, "books")

    /** Ids of the books in a library that are on the device. */
    fun downloadedIds(libraryId: String): Flow<Set<Int>> =
        dao.observeDownloads(libraryId).map { list -> list.map { it.bookId }.toSet() }

    /**
     * The book's file, downloading it first if it isn't on the device yet.
     * [onProgress] gets 0–1, or null while the size is unknown.
     */
    suspend fun fileFor(session: Session, book: CalibreBook, format: String, onProgress: (Float?) -> Unit): File {
        dao.download(session.libraryId, book.id)?.let { existing ->
            val file = File(existing.path)
            if (existing.format == format && file.exists()) return file
        }
        val file = File(File(root, safeName(session.libraryId)), "${book.id}.${format.lowercase()}")
        session.client.downloadFormat(session.libraryId, book.id, format, file, onProgress)
        dao.download(session.libraryId, book.id)?.let { old -> if (old.path != file.path) File(old.path).delete() }
        dao.saveDownload(DownloadEntity(session.libraryId, book.id, format, file.path, file.length(), System.currentTimeMillis()))
        return file
    }

    suspend fun remove(libraryId: String, bookId: Int) = withContext(Dispatchers.IO) {
        dao.download(libraryId, bookId)?.let { File(it.path).delete() }
        dao.deleteDownload(libraryId, bookId)
    }

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /** Formats EasyReader can show itself, best first (the owner chose EPUB over PDF). */
        val READABLE = listOf("EPUB", "PDF")

        /** Which format to open: EPUB, then PDF, then anything else (opened in another app). */
        fun bestFormat(book: CalibreBook): String? =
            READABLE.firstOrNull { it in book.formats } ?: book.formats.firstOrNull()
    }
}
