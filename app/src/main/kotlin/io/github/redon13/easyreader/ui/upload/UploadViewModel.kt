package io.github.redon13.easyreader.ui.upload

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.redon13.easyreader.EasyReaderApp
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.calibre.UploadResult
import io.github.redon13.easyreader.data.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One file being sent to Calibre. */
data class UploadItem(
    val id: Long,
    val fileName: String,
    /** 0–1 while sending; null while waiting or if the size is unknown. */
    val progress: Float? = null,
    val status: Status = Status.WAITING,
    /** e.g. "Added: Emma by Jane Austen" or the problem. */
    val message: String? = null,
) {
    enum class Status { WAITING, SENDING, ADDED, ALREADY_THERE, FAILED }
}

/** Sends chosen files to Calibre one at a time, keeping going while the owner looks elsewhere in the app. */
class UploadViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as EasyReaderApp).container
    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()
    private val queue = Mutex()
    private var nextId = 0L

    fun add(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        uris.forEach { uri ->
            var name = uri.lastPathSegment ?: "book"
            var size: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val item = UploadItem(nextId++, name)
            _items.update { it + item }
            viewModelScope.launch { queue.withLock { send(item, uri, size) } }
        }
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.status == UploadItem.Status.WAITING || it.status == UploadItem.Status.SENDING } }
    }

    private suspend fun send(item: UploadItem, uri: Uri, size: Long?) {
        val state = container.sessions.state.value as? SessionState.SignedIn
        if (state == null) {
            change(item.id) { it.copy(status = UploadItem.Status.FAILED, message = "Please sign in first.") }
            return
        }
        val session = state.session
        if (!item.fileName.contains('.')) {
            change(item.id) { it.copy(status = UploadItem.Status.FAILED, message = "The file name has no extension (like .epub), so Calibre can't tell what it is.") }
            return
        }
        change(item.id) { it.copy(status = UploadItem.Status.SENDING, progress = 0f) }
        val resolver = getApplication<Application>().contentResolver
        try {
            val result = session.client.uploadBook(
                session.libraryId,
                item.fileName,
                size,
                open = { resolver.openInputStream(uri) ?: error("The file couldn't be read") },
                onProgress = { p -> change(item.id) { it.copy(progress = p) } },
            )
            val who = result.title + (result.authors.takeIf { it.isNotEmpty() }?.joinToString(" & ", prefix = " by ") ?: "")
            change(item.id) {
                when (result) {
                    is UploadResult.Added -> it.copy(status = UploadItem.Status.ADDED, progress = 1f, message = "Added: $who")
                    is UploadResult.Duplicate -> it.copy(status = UploadItem.Status.ALREADY_THERE, progress = 1f, message = "Already in Calibre: $who")
                }
            }
            if (result is UploadResult.Added) container.library.sync(session)
        } catch (e: CalibreException) {
            change(item.id) {
                it.copy(
                    status = UploadItem.Status.FAILED,
                    message = if (e is CalibreException.NotAllowed) "Calibre refused the file. Check that your Calibre user is allowed to make changes." else e.message,
                )
            }
        } catch (e: Exception) {
            change(item.id) { it.copy(status = UploadItem.Status.FAILED, message = "The file couldn't be read.") }
        }
    }

    private fun change(id: Long, f: (UploadItem) -> UploadItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }
}
