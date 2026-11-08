package io.github.redon13.easyreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        PositionEntity::class,
        DownloadEntity::class,
        DeviceBookEntity::class,
        PendingChangeEntity::class,
        SmartShelfEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun reading(): ReadingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "easyreader.db")
                // Version 1 only held a copy of the library, which is simply
                // downloaded again. Later versions hold reading positions, so
                // they are upgraded step by step and nothing is lost.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .addMigrations(MIGRATION_2_3)
                .build()

        /** Stage 4: changes waiting to be sent to Calibre, and smart shelves. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_changes` (`libraryId` TEXT NOT NULL, `bookId` INTEGER NOT NULL, " +
                        "`lookupName` TEXT NOT NULL, `valueJson` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`libraryId`, `bookId`, `lookupName`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `smart_shelves` (`libraryId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `bookIds` TEXT NOT NULL, PRIMARY KEY(`libraryId`, `kind`, `name`))"
                )
            }
        }
    }
}
