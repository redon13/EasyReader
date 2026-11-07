package io.github.redon13.easyreader

import android.content.Context
import io.github.redon13.easyreader.data.LibraryRepository
import io.github.redon13.easyreader.data.SessionManager
import io.github.redon13.easyreader.data.SettingsStore
import io.github.redon13.easyreader.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Creates the app's shared parts once, at start-up. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val database = AppDatabase.create(context)
    val sessions = SessionManager(settings, database.books(), appScope)
    val library = LibraryRepository(database.books(), settings, sessions)
}
