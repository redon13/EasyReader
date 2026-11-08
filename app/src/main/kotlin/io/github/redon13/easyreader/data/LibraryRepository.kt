package io.github.redon13.easyreader.data

import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.data.db.BookDao
import io.github.redon13.easyreader.data.db.BookEntity
import io.github.redon13.easyreader.data.db.ReadingDao
import io.github.redon13.easyreader.data.db.SmartShelfEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the on-device copy of the library up to date with Calibre. */
class LibraryRepository(
    private val books: BookDao,
    private val reading: ReadingDao,
    private val settings: SettingsStore,
    private val sessions: SessionManager,
    private val changes: BookChanges,
) {
    private val mutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Libraries on the server (id → name), known after the first sync. */
    private val _libraries = MutableStateFlow<Map<String, String>>(emptyMap())
    val libraries: StateFlow<Map<String, String>> = _libraries.asStateFlow()

    /** Result of the last custom-column check, or null if not checked yet. */
    private val _columns = MutableStateFlow<ColumnCheck?>(null)
    val columns: StateFlow<ColumnCheck?> = _columns.asStateFlow()

    val lastSynced: Flow<Long?> = settings.lastSynced

    fun books(libraryId: String): Flow<List<BookEntity>> = books.observeLibrary(libraryId)

    /** Calibre's virtual libraries and saved searches, with their books (as of the last sync). */
    fun smartShelves(libraryId: String): Flow<List<SmartShelfEntity>> = reading.observeSmartShelves(libraryId)

    /**
     * Downloads the library's details (not the book files) from Calibre.
     * Returns an error message for the owner, or null if it worked.
     */
    suspend fun sync(session: Session): String? = mutex.withLock {
        _syncing.value = true
        try {
            val client = session.client
            val libs = client.libraries()
            _libraries.value = libs.libraries
            if (session.libraryId !in libs.libraries) {
                // The library was removed or renamed on the PC: fall back to the default one.
                sessions.switchLibrary(libs.defaultLibraryId, libs.libraries[libs.defaultLibraryId] ?: libs.defaultLibraryId)
                return@withLock null
            }
            _columns.value = client.checkColumns(session.libraryId)
            // Send changes made on this device first, so the fresh copy includes them.
            changes.sendPending()
            val ids = client.allBookIds(session.libraryId)
            val fetched = client.books(session.libraryId, ids)
            books.replaceLibrary(session.libraryId, fetched.map { BookEntity.from(session.libraryId, it) })
            // Smart shelves are extra: a problem reading them never stops the sync.
            runCatching {
                val smart = client.smartShelves(session.libraryId).map { shelf ->
                    SmartShelfEntity(
                        session.libraryId,
                        shelf.kind.name,
                        shelf.name,
                        runCatching { client.smartShelfBookIds(session.libraryId, shelf) }.getOrDefault(emptyList()).joinToString(","),
                    )
                }
                reading.deleteSmartShelves(session.libraryId)
                reading.saveSmartShelves(smart)
            }
            settings.setLastSynced(System.currentTimeMillis())
            null
        } catch (e: CalibreException.WrongLogin) {
            sessions.signOut("The server didn't accept the saved password. Please sign in again.")
            null
        } catch (e: CalibreException) {
            e.message
        } finally {
            _syncing.value = false
        }
    }
}
