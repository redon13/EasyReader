package io.github.redon13.easyreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.AppContainer
import io.github.redon13.easyreader.data.SessionState
import io.github.redon13.easyreader.ui.library.LibraryScreen
import io.github.redon13.easyreader.ui.login.LoginScreen
import io.github.redon13.easyreader.ui.theme.EasyColors

/** Shows the sign-in screen or the library, depending on whether the owner is signed in. */
@Composable
fun AppRoot(container: AppContainer) {
    val state by container.sessions.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(EasyColors.Background))
        is SessionState.SignedOut -> LoginScreen(container, s)
        is SessionState.SignedIn -> key(s.session.libraryId) {
            LibraryScreen(container, s.session)
        }
    }
}
