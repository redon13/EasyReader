package io.github.redon13.easyreader.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.data.SessionManager
import io.github.redon13.easyreader.data.SessionState
import kotlinx.coroutines.launch

class LoginViewModel(
    private val sessions: SessionManager,
    initial: SessionState.SignedOut,
) : ViewModel() {
    var address by mutableStateOf(initial.serverUrl)
    var username by mutableStateOf(initial.username)
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf(initial.message)
        private set

    val canConnect get() = !busy && address.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    fun connect() {
        if (!canConnect) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                sessions.signIn(address, username, password)
            } catch (e: CalibreException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}
