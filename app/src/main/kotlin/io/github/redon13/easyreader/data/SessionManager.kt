package io.github.redon13.easyreader.data

import io.github.redon13.easyreader.calibre.CalibreClient
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.data.db.BookDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Who is signed in to which server and library. */
data class Session(
    val client: CalibreClient,
    val libraryId: String,
    val libraryName: String,
)

sealed interface SessionState {
    data object Loading : SessionState
    data class SignedOut(
        val serverUrl: String = "",
        val username: String = "",
        /** Shown on the sign-in screen, e.g. after the password was changed. */
        val message: String? = null,
    ) : SessionState
    data class SignedIn(val session: Session) : SessionState
}

class SessionManager(
    private val settings: SettingsStore,
    private val books: BookDao,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The signed-in client, if any. Used for loading covers. */
    val currentClient: CalibreClient?
        get() = (state.value as? SessionState.SignedIn)?.session?.client

    init {
        // Signing in again on start-up is done from what's saved, without
        // contacting the server, so the library opens even when offline.
        scope.launch {
            val saved = settings.savedLogin()
            val url = saved?.serverUrl?.toHttpUrlOrNull()
            _state.value = if (saved != null && url != null && saved.password != null && saved.libraryId != null) {
                SessionState.SignedIn(
                    Session(
                        CalibreClient(url, saved.username, saved.password),
                        saved.libraryId,
                        saved.libraryName ?: saved.libraryId,
                    )
                )
            } else {
                SessionState.SignedOut(saved?.serverUrl.orEmpty(), saved?.username.orEmpty())
            }
        }
    }

    /** Checks the details with the server and, if they work, saves them. Throws [CalibreException]. */
    suspend fun signIn(address: String, username: String, password: String) {
        val url = CalibreClient.normaliseAddress(address) ?: throw CalibreException.BadAddress()
        val client = CalibreClient(url, username.trim(), password)
        val libraries = client.libraries()

        val previous = settings.savedLogin()
        val sameServer = previous?.serverUrl == url.toString()
        if (!sameServer) books.deleteAll()

        val libraryId = previous?.libraryId?.takeIf { sameServer && it in libraries.libraries }
            ?: libraries.defaultLibraryId
        val libraryName = libraries.libraries[libraryId] ?: libraryId

        settings.saveLogin(url.toString(), username.trim(), password)
        settings.saveLibrary(libraryId, libraryName)
        _state.value = SessionState.SignedIn(Session(client, libraryId, libraryName))
    }

    suspend fun switchLibrary(libraryId: String, libraryName: String) {
        val current = (state.value as? SessionState.SignedIn)?.session ?: return
        if (current.libraryId == libraryId) return
        settings.saveLibrary(libraryId, libraryName)
        _state.value = SessionState.SignedIn(current.copy(libraryId = libraryId, libraryName = libraryName))
    }

    suspend fun signOut(message: String? = null) {
        settings.signOut()
        val saved = settings.savedLogin()
        _state.value = SessionState.SignedOut(saved?.serverUrl.orEmpty(), saved?.username.orEmpty(), message)
    }
}
