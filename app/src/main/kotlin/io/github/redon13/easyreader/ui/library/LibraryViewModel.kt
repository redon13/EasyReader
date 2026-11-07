package io.github.redon13.easyreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.data.LibraryRepository
import io.github.redon13.easyreader.data.Session
import io.github.redon13.easyreader.data.SessionManager
import io.github.redon13.easyreader.data.db.BookEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repo: LibraryRepository,
    private val sessions: SessionManager,
    val session: Session,
) : ViewModel() {

    /** Null until the stored copy has been read. */
    val books: StateFlow<List<BookEntity>?> = repo.books(session.libraryId)
        .map<List<BookEntity>, List<BookEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> = repo.syncing
    val libraries: StateFlow<Map<String, String>> = repo.libraries
    val columns: StateFlow<ColumnCheck?> = repo.columns
    val lastSynced: StateFlow<Long?> = repo.lastSynced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            repo.sync(session)?.let { _messages.tryEmit("Couldn't sync: $it") }
        }
    }

    fun coverUrl(book: BookEntity): String =
        session.client.thumbnailUrl(book.libraryId, book.id, book.lastModified)

    fun switchLibrary(id: String, name: String) {
        viewModelScope.launch { sessions.switchLibrary(id, name) }
    }

    fun signOut() {
        viewModelScope.launch { sessions.signOut() }
    }
}
