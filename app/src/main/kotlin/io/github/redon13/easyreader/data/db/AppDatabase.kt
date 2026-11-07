package io.github.redon13.easyreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "easyreader.db")
                // For now the database only holds a copy of the library, which is
                // simply downloaded again if its layout changes in an update.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
