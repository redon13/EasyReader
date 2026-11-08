package io.github.redon13.easyreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    // Positions

    @Query("SELECT * FROM positions WHERE libraryId = :libraryId")
    fun observePositions(libraryId: String): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE libraryId = :libraryId AND bookId = :bookId")
    suspend fun position(libraryId: String, bookId: Int): PositionEntity?

    @Query("SELECT * FROM positions WHERE pending = 1")
    suspend fun pendingPositions(): List<PositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: PositionEntity)

    /** Marks a position as sent, unless a newer one was saved meanwhile. */
    @Query("UPDATE positions SET pending = 0 WHERE libraryId = :libraryId AND bookId = :bookId AND timestamp = :timestamp")
    suspend fun markSent(libraryId: String, bookId: Int, timestamp: Long)

    // Downloads

    @Query("SELECT * FROM downloads WHERE libraryId = :libraryId")
    fun observeDownloads(libraryId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE libraryId = :libraryId AND bookId = :bookId")
    suspend fun download(libraryId: String, bookId: Int): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE libraryId = :libraryId AND bookId = :bookId")
    suspend fun deleteDownload(libraryId: String, bookId: Int)

    // Books opened from the device

    @Query("SELECT * FROM device_books WHERE hash = :hash")
    suspend fun deviceBook(hash: String): DeviceBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDeviceBook(book: DeviceBookEntity)

    @Query("UPDATE device_books SET positionJson = :json WHERE hash = :hash")
    suspend fun saveDeviceBookPosition(hash: String, json: String)

    // Changes waiting to be sent to Calibre

    @Query("SELECT * FROM pending_changes ORDER BY timestamp")
    suspend fun pendingChanges(): List<PendingChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePendingChange(change: PendingChangeEntity)

    /** Removes a sent change, unless a newer one for the same column was saved meanwhile. */
    @Query("DELETE FROM pending_changes WHERE libraryId = :libraryId AND bookId = :bookId AND lookupName = :lookupName AND timestamp = :timestamp")
    suspend fun deletePendingChange(libraryId: String, bookId: Int, lookupName: String, timestamp: Long)

    // Smart shelves

    @Query("SELECT * FROM smart_shelves WHERE libraryId = :libraryId ORDER BY name COLLATE NOCASE")
    fun observeSmartShelves(libraryId: String): Flow<List<SmartShelfEntity>>

    @Query("DELETE FROM smart_shelves WHERE libraryId = :libraryId")
    suspend fun deleteSmartShelves(libraryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSmartShelves(shelves: List<SmartShelfEntity>)
}
