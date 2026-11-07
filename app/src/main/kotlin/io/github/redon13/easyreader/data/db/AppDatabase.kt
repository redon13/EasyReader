package io.github.redon13.easyreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, PositionEntity::class, DownloadEntity::class, DeviceBookEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun reading(): ReadingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "easyreader.db")
                // Version 1 only held a copy of the library, which is simply
                // downloaded again. From version 2 on, the database holds reading
                // positions, so every later change needs a proper upgrade step.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
    }
}
