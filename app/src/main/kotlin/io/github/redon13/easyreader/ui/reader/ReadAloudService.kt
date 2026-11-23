package io.github.redon13.easyreader.ui.reader

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps read-aloud going when the screen is off or another app is in front,
 * and shows the play/pause controls in the notification and on the lock screen.
 */
class ReadAloudService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val player = currentPlayer ?: return null
        session?.let { if (it.player === player) return it }
        // A different book: replace the old session.
        session?.run {
            removeSession(this)
            release()
        }
        return MediaSession.Builder(this, player).build().also { session = it }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping EasyReader away stops reading unless it's playing.
        val player = session?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            removeSession(this)
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** The read-aloud player, set by the reader while a book is being read aloud. */
        @Volatile
        var currentPlayer: Player? = null
    }
}
