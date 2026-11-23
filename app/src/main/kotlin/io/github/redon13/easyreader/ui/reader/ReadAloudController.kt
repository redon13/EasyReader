@file:OptIn(ExperimentalReadiumApi::class, ExperimentalCoroutinesApi::class)

package io.github.redon13.easyreader.ui.reader

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.redon13.easyreader.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.tokenizer.TextTokenizer
import org.readium.r2.shared.util.tokenizer.Tokenizer

/** What the read-aloud player bar shows. */
data class ReadAloudUi(
    val playing: Boolean,
    /** e.g. "Chirp 3 HD · Iapetus" or "Phone voice". */
    val voice: String,
    val speed: Double,
    val notice: String?,
)

/** Where read-aloud is: the paragraph, and the sentence or word being spoken. */
data class ReadAloudPlace(val paragraph: Locator, val spoken: Locator?)

/** Starts, controls and stops reading a book aloud. One per open book. */
class ReadAloudController(
    private val app: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val messages: MutableSharedFlow<String>,
) {
    private val provider = ReadAloudEngineProvider(app, container.settings, container.googleTts)
    private var navigator: TtsNavigator<ReadAloudSettings, ReadAloudPreferences, ReadAloudError, ReadAloudVoice>? = null
    private var player: Player? = null
    private var controller: java.util.concurrent.Future<MediaController>? = null
    private var watching: Job? = null
    private var starting = false

    private val _ui = MutableStateFlow<ReadAloudUi?>(null)
    /** Null when not reading aloud. */
    val ui: StateFlow<ReadAloudUi?> = _ui.asStateFlow()

    private val _place = MutableStateFlow<ReadAloudPlace?>(null)
    val place: StateFlow<ReadAloudPlace?> = _place.asStateFlow()

    /** Starts reading from [from] (the top of the page showing), or carries on if already started. */
    fun start(publication: Publication, from: Locator?) {
        player?.let { it.play(); return }
        if (starting) return
        starting = true
        scope.launch {
            try {
                val config = container.settings.readAloudConfig.first()
                _ui.value = ReadAloudUi(playing = false, voice = "Starting…", speed = config.speed, notice = null)
                val factory = TtsNavigatorFactory(
                    application = app,
                    publication = publication,
                    ttsEngineProvider = provider,
                    tokenizerFactory = { _: Language? -> paragraphs },
                )
                if (factory == null) {
                    _ui.value = null
                    messages.tryEmit("This book can't be read aloud.")
                    return@launch
                }
                val listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() = stop()
                }
                val nav = factory.createNavigator(listener, from, ReadAloudPreferences(speed = config.speed))
                    .getOrElse {
                        _ui.value = null
                        messages.tryEmit("Read aloud couldn't start. (${it.message})")
                        return@launch
                    }
                navigator = nav
                val p = nav.asMedia3Player()
                player = p
                ReadAloudService.currentPlayer = p
                // Connecting to the service keeps reading going with the screen off.
                controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, ReadAloudService::class.java))).buildAsync()
                watch(nav)
                p.play()
            } finally {
                starting = false
            }
        }
    }

    private fun watch(nav: TtsNavigator<ReadAloudSettings, ReadAloudPreferences, ReadAloudError, ReadAloudVoice>) {
        watching = scope.launch {
            launch {
                nav.location.collect { _place.value = ReadAloudPlace(it.utteranceLocator, it.tokenLocator) }
            }
            launch {
                nav.playback.collect { pb ->
                    when (val state = pb.state) {
                        is TtsNavigator.State.Ended -> {
                            messages.tryEmit("Finished reading aloud.")
                            stop()
                        }
                        is TtsNavigator.State.Failure -> {
                            messages.tryEmit("Read aloud stopped: ${state.error.message}")
                            stop()
                        }
                        else -> Unit
                    }
                }
            }
            launch {
                val engineState = provider.engine.flatMapLatest { engine ->
                    if (engine == null) flowOf(null to null) else combine(engine.tier, engine.notice) { t, n -> t to n }
                }
                combine(nav.playback, engineState, container.settings.readAloudConfig, nav.settings) { pb, (tier, notice), config, s ->
                    ReadAloudUi(
                        playing = pb.playWhenReady,
                        voice = when (tier) {
                            VoiceTier.Chirp -> "${tier.label} · ${config.voiceName}"
                            null -> if (config.apiKey == null) VoiceTier.Phone.label else "Starting…"
                            else -> tier.label
                        },
                        speed = s.speed,
                        notice = notice,
                    )
                }.collect { if (navigator === nav) _ui.value = it }
            }
        }
    }

    fun playPause() {
        val p = player ?: return
        if (p.playWhenReady) p.pause() else p.play()
    }

    fun next() {
        navigator?.skipToNextUtterance()
    }

    fun previous() {
        navigator?.skipToPreviousUtterance()
    }

    /** Steps through the speeds: 0.75×, 1×, 1.25×, 1.5×, 2×. */
    fun nextSpeed() {
        val nav = navigator ?: return
        val current = nav.settings.value.speed
        val speed = SPEEDS.firstOrNull { it > current + 0.01 } ?: SPEEDS.first()
        nav.submitPreferences(ReadAloudPreferences(speed = speed))
        scope.launch {
            val config = container.settings.readAloudConfig.first()
            container.settings.saveReadAloudConfig(config.copy(speed = speed))
        }
    }

    fun stop() {
        watching?.cancel()
        watching = null
        navigator?.close()
        navigator = null
        player = null
        ReadAloudService.currentPlayer = null
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        app.stopService(Intent(app, ReadAloudService::class.java))
        _ui.value = null
        _place.value = null
    }

    private companion object {
        val SPEEDS = listOf(0.75, 1.0, 1.25, 1.5, 2.0)

        /** Reads a paragraph at a time, so Google's voice flows naturally with few pauses. */
        val paragraphs: TextTokenizer = Tokenizer { text ->
            val first = text.indexOfFirst { !it.isWhitespace() }
            if (first < 0) emptyList() else listOf(first..text.indexOfLast { !it.isWhitespace() })
        }
    }
}
