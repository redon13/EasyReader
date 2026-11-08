package io.github.redon13.easyreader.data

import android.content.Context
import io.github.redon13.easyreader.calibre.CalibreBookParser
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.calibre.RequiredColumn
import io.github.redon13.easyreader.data.db.BookDao
import io.github.redon13.easyreader.data.db.PendingChangeEntity
import io.github.redon13.easyreader.data.db.ReadingDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shelves, favourites and finished status.
 *
 * A change shows in the app straight away (the stored copy of the book is
 * updated), is queued on the device, and is sent to Calibre's custom column.
 * If the device is offline, the background task sends it later.
 */
class BookChanges(
    private val context: Context,
    private val dao: ReadingDao,
    private val books: BookDao,
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    suspend fun setFavourite(libraryId: String, bookId: Int, favourite: Boolean) =
        set(libraryId, bookId, RequiredColumn.FAVOURITE.lookupName, JsonPrimitive(favourite))

    suspend fun setFinished(libraryId: String, bookId: Int, finished: Boolean) =
        set(libraryId, bookId, RequiredColumn.FINISHED.lookupName, JsonPrimitive(finished))

    suspend fun setShelves(libraryId: String, bookId: Int, shelves: List<String>) =
        set(libraryId, bookId, RequiredColumn.SHELVES.lookupName, JsonArray(shelves.distinct().map(::JsonPrimitive)))

    private suspend fun set(libraryId: String, bookId: Int, lookupName: String, value: JsonElement) {
        books.book(libraryId, bookId)?.let { entity ->
            books.insertAll(listOf(entity.copy(rawJson = CalibreBookParser.withCustomValue(entity.rawJson, lookupName, value))))
        }
        dao.savePendingChange(PendingChangeEntity(libraryId, bookId, lookupName, value.toString(), System.currentTimeMillis()))
        scope.launch { if (!sendPending()) PositionSync.schedule(context) }
    }

    /** Sends every waiting change. Returns true if nothing is left waiting. */
    suspend fun sendPending(): Boolean = mutex.withLock {
        val state = sessions.state.first { it !is SessionState.Loading }
        val session = (state as? SessionState.SignedIn)?.session ?: return@withLock false
        var allSent = true
        for (c in dao.pendingChanges()) {
            try {
                session.client.setCustomColumn(c.libraryId, c.bookId, c.lookupName, Json.parseToJsonElement(c.valueJson))
                dao.deletePendingChange(c.libraryId, c.bookId, c.lookupName, c.timestamp)
            } catch (e: CalibreException.NotAllowed) {
                // The book was removed, or the user can't make changes: drop it.
                dao.deletePendingChange(c.libraryId, c.bookId, c.lookupName, c.timestamp)
            } catch (e: CalibreException) {
                allSent = false
                if (e is CalibreException.CannotReach) break
            }
        }
        allSent
    }
}
