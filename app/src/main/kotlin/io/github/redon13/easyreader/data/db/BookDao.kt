package io.github.redon13.easyreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** All books in a library, most recently added first. */
    @Query("SELECT * FROM books WHERE libraryId = :libraryId ORDER BY timestamp DESC, id DESC")
    fun observeLibrary(libraryId: String): Flow<List<BookEntity>>

    @Query("DELETE FROM books WHERE libraryId = :libraryId")
    suspend fun deleteLibrary(libraryId: String)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    /** Swaps the stored copy of a library for a fresh one in one go. */
    @Transaction
    suspend fun replaceLibrary(libraryId: String, books: List<BookEntity>) {
        deleteLibrary(libraryId)
        insertAll(books)
    }
}
