package io.github.redon13.easyreader.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.redon13.easyreader.EasyReaderApp
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.CalibreBookParser
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.data.db.BookDao
import io.github.redon13.easyreader.data.db.DownloadEntity
import io.github.redon13.easyreader.data.db.ReadingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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

    /**
     * Removes downloads of books that were finished 7 or more days ago. The
     * clock starts when this device first sees the book as finished; marking a
     * book unread again stops it.
     */
    suspend fun cleanUp(books: BookDao, enabled: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (d in dao.allDownloads()) {
            val entity = books.book(d.libraryId, d.bookId)
            val finished = entity != null && CalibreBookParser.parse(entity.id, entity.rawJson).isFinished
            when {
                !finished -> if (d.finishedSeenAt != null) dao.setFinishedSeenAt(d.libraryId, d.bookId, null)
                d.finishedSeenAt == null -> dao.setFinishedSeenAt(d.libraryId, d.bookId, now)
                enabled && now - d.finishedSeenAt >= KEEP_FINISHED_MS -> remove(d.libraryId, d.bookId)
            }
        }
    }

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val KEEP_FINISHED_MS = 7L * 24 * 60 * 60 * 1000

        /** Asks Android to run the clean-up about once a day. */
        fun scheduleCleanUp(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanUpWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("clean-up", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Formats EasyReader can show itself, best first (the owner chose EPUB over PDF). */
        val READABLE = listOf("EPUB", "PDF")

        /** Which format to open: EPUB, then PDF, then anything else (opened in another app). */
        fun bestFormat(book: CalibreBook): String? =
            READABLE.firstOrNull { it in book.formats } ?: book.formats.firstOrNull()
    }
}

/** Background task: removes downloads of books finished 7+ days ago (if turned on in Settings). */
class CleanUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EasyReaderApp).container
        val enabled = container.settings.appSettings.first().cleanUpFinished
        container.downloads.cleanUp(container.database.books(), enabled)
        return Result.success()
    }
}
