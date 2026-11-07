package io.github.redon13.easyreader.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.redon13.easyreader.EasyReaderApp
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.calibre.ReadingPosition
import io.github.redon13.easyreader.calibre.serverPosition
import io.github.redon13.easyreader.data.db.PositionEntity
import io.github.redon13.easyreader.data.db.ReadingDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the owner's place in each Calibre book in step between devices.
 *
 * Positions are saved on the device first, then sent to Calibre's
 * #er_position column. If the device is offline, they wait and are sent by a
 * background task once it's connected. Whenever two positions disagree, the
 * most recently saved one wins.
 */
class PositionSync(
    private val context: Context,
    private val dao: ReadingDao,
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** A name for this device, e.g. "Galaxy Tab S9 FE", saved alongside each position. */
    val deviceName: String by lazy {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
    }

    /** Positions saved on this device (or fetched when a book was opened), by book id. */
    fun localPositions(libraryId: String): Flow<Map<Int, ReadingPosition>> =
        dao.observePositions(libraryId).map { list ->
            list.mapNotNull { e -> ReadingPosition.parse(e.json)?.let { e.bookId to it } }.toMap()
        }

    /** Saves a position on the device straight away. Call [send] to pass it on to Calibre. */
    suspend fun save(libraryId: String, bookId: Int, position: ReadingPosition) {
        dao.savePosition(PositionEntity(libraryId, bookId, position.toJson(), position.timestamp, pending = true))
    }

    /** Sends waiting positions to Calibre now; if that isn't possible, a background task tries later. */
    fun send() {
        scope.launch {
            if (!sendPending()) schedule(context)
        }
    }

    /**
     * The newest position for a book: this device's or Calibre's. Asks Calibre
     * first (giving up after a few seconds if it can't be reached).
     */
    suspend fun latest(session: Session, bookId: Int): ReadingPosition? {
        val local = dao.position(session.libraryId, bookId)?.let { ReadingPosition.parse(it.json) }
        val remote = withTimeoutOrNull(6_000) {
            runCatching { session.client.book(session.libraryId, bookId)?.serverPosition }.getOrNull()
        }
        if (remote != null && remote.isNewerThan(local)) {
            dao.savePosition(PositionEntity(session.libraryId, bookId, remote.toJson(), remote.timestamp, pending = false))
            return remote
        }
        return local
    }

    /** Sends every waiting position. Returns true if nothing is left waiting. */
    suspend fun sendPending(): Boolean = mutex.withLock {
        val state = sessions.state.first { it !is SessionState.Loading }
        val session = (state as? SessionState.SignedIn)?.session ?: return@withLock false
        var allSent = true
        for (p in dao.pendingPositions()) {
            try {
                val mine = ReadingPosition.parse(p.json)
                val theirs = session.client.book(p.libraryId, p.bookId)?.serverPosition
                if (mine == null) {
                    dao.markSent(p.libraryId, p.bookId, p.timestamp)
                } else if (theirs != null && theirs.isNewerThan(mine)) {
                    // Another device saved a newer place meanwhile: keep theirs.
                    dao.savePosition(PositionEntity(p.libraryId, p.bookId, theirs.toJson(), theirs.timestamp, pending = false))
                } else {
                    session.client.setCustomColumn(p.libraryId, p.bookId, ReadingPosition.COLUMN, p.json)
                    dao.markSent(p.libraryId, p.bookId, p.timestamp)
                }
            } catch (e: CalibreException.NotAllowed) {
                // The book was removed, or the user can't make changes: nothing more to do.
                dao.markSent(p.libraryId, p.bookId, p.timestamp)
            } catch (e: CalibreException) {
                allSent = false
            }
        }
        allSent
    }

    companion object {
        private const val WORK_NAME = "send-positions"

        /** Asks Android to run [PositionSyncWorker] as soon as there's a connection. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PositionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Background task that sends reading positions saved while offline. */
class PositionSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EasyReaderApp).container
        return if (container.positions.sendPending()) Result.success() else Result.retry()
    }
}
