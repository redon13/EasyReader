@file:OptIn(ExperimentalReadiumApi::class)

package io.github.redon13.easyreader.ui.reader

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import io.github.redon13.easyreader.data.GoogleTts
import io.github.redon13.easyreader.data.GoogleTtsException
import io.github.redon13.easyreader.data.GoogleVoice
import io.github.redon13.easyreader.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Reading speed (and, if ever needed, language) for read-aloud. */
data class ReadAloudPreferences(
    override val language: Language? = null,
    val speed: Double? = null,
) : TtsEngine.Preferences<ReadAloudPreferences> {
    override fun plus(other: ReadAloudPreferences) = ReadAloudPreferences(
        language = other.language ?: language,
        speed = other.speed ?: speed,
    )
}

data class ReadAloudSettings(
    override val language: Language?,
    val speed: Double,
) : TtsEngine.Settings {
    override val overrideContentLanguage: Boolean get() = false
}

data class ReadAloudVoice(override val language: Language) : TtsEngine.Voice

/** Only raised when even the phone's voice can't speak. */
class ReadAloudError(override val message: String) : TtsEngine.Error {
    override val cause: Error? get() = null
}

class ReadAloudPreferencesEditor(initial: ReadAloudPreferences) : PreferencesEditor<ReadAloudPreferences> {
    override var preferences: ReadAloudPreferences = initial
        private set

    override fun clear() {
        preferences = ReadAloudPreferences()
    }
}

/** Which voice is speaking right now, for the player bar. */
enum class VoiceTier(val label: String) { Chirp("Chirp 3 HD"), WaveNet("WaveNet"), Phone("Phone voice") }

/**
 * EasyReader's read-aloud voice. Each piece of text is spoken by the best voice
 * still available: Google Chirp 3 HD while this month's Chirp allowance lasts,
 * then Google WaveNet, then the phone's own voice (also used offline or
 * without a key). Speech is fetched a little ahead and cached, so pausing and
 * replaying don't cost anything.
 */
class ReadAloudEngine private constructor(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val google: GoogleTts,
    private val phone: TextToSpeech?,
    private val defaultLanguage: Language?,
    initialPreferences: ReadAloudPreferences,
) : TtsEngine<ReadAloudSettings, ReadAloudPreferences, ReadAloudError, ReadAloudVoice> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private var listener: TtsEngine.Listener<ReadAloudError>? = null

    private val _settings = MutableStateFlow(resolve(initialPreferences))
    override val settings: StateFlow<ReadAloudSettings> = _settings.asStateFlow()

    private val _tier = MutableStateFlow<VoiceTier?>(null)
    /** The voice type in use; null before anything has been spoken. */
    val tier: StateFlow<VoiceTier?> = _tier.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** A plain-words note when Google can't be used (bad key, offline…). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    override val voices: Set<ReadAloudVoice> = emptySet()

    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var onPlayerFinished: ((Boolean) -> Unit)? = null

    // Where a paused paragraph stopped, so resuming carries on from there.
    private data class Resume(val text: String, val chunk: Int, val millis: Int)
    private var resume: Resume? = null
    private var current: Resume? = null

    private var offlineUntil = 0L
    private var wavenetVoices = mutableMapOf<String, String?>()
    private val phoneWaiters = mutableMapOf<String, (Boolean) -> Unit>()
    private var phoneRange: ((IntRange) -> Unit)? = null

    init {
        phone?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) = finishPhone(utteranceId, true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finishPhone(utteranceId, false)
            override fun onError(utteranceId: String, errorCode: Int) = finishPhone(utteranceId, false)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finishPhone(utteranceId, false)
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { phoneRange?.invoke(start until end) }
            }
        })
    }

    private fun finishPhone(id: String, ok: Boolean) {
        main.post { phoneWaiters.remove(id)?.invoke(ok) }
    }

    private fun resolve(p: ReadAloudPreferences) =
        ReadAloudSettings(language = p.language, speed = (p.speed ?: 1.0).coerceIn(0.5, 3.0))

    override fun submitPreferences(preferences: ReadAloudPreferences) {
        _settings.value = resolve(preferences)
        player?.let { mp -> runCatching { if (mp.isPlaying) mp.playbackParams = mp.playbackParams.setSpeed(speed()) } }
    }

    override fun setListener(listener: TtsEngine.Listener<ReadAloudError>?) {
        this.listener = listener
    }

    private fun speed() = _settings.value.speed.toFloat()

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        job?.cancel()
        stopAudio()
        val from = resume?.takeIf { it.text == text }
        resume = null
        job = scope.launch {
            val lang = language ?: _settings.value.language ?: defaultLanguage ?: Language(Locale.getDefault())
            listener?.onStart(requestId)
            val ok = speakText(requestId, text, lang, from)
            current = null
            if (ok) listener?.onDone(requestId)
            else listener?.onError(requestId, ReadAloudError("The phone's voice couldn't read this."))
        }
    }

    override fun stop() {
        // Remember where we were, so "play" after "pause" continues mid-paragraph.
        resume = current?.let { c -> c.copy(millis = runCatching { player?.currentPosition }.getOrNull() ?: 0) }
        job?.cancel()
        job = null
        stopAudio()
    }

    override fun close() {
        stop()
        scope.cancel()
        runCatching { phone?.shutdown() }
    }

    private fun stopAudio() {
        onPlayerFinished = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        phoneRange = null
        if (phoneWaiters.isNotEmpty()) {
            phoneWaiters.clear()
            runCatching { phone?.stop() }
        }
    }

    // ---- Speaking one paragraph ----

    private suspend fun speakText(id: TtsEngine.RequestId, text: String, lang: Language, from: Resume?): Boolean {
        val chunks = chunk(text)
        if (chunks.isEmpty()) return true
        val start = from?.chunk?.coerceIn(chunks.indices) ?: 0
        val voiceLang = googleLanguage(lang)
        var next: Deferred<File?>? = scope.async { fetch(text.substring(chunks[start]), voiceLang) }
        for (i in start until chunks.size) {
            val audio = next!!.await()
            next = if (i + 1 < chunks.size) scope.async { fetch(text.substring(chunks[i + 1]), voiceLang) } else null
            current = Resume(text, i, 0)
            val seek = if (from != null && i == start) (from.millis - 1500).coerceAtLeast(0) else 0
            val played = audio != null && playFile(id, audio, text, chunks[i], seek)
            if (!played) {
                _tier.value = VoiceTier.Phone
                if (!speakWithPhone(id, text, chunks[i], lang)) {
                    next?.cancel()
                    return false
                }
            }
        }
        return true
    }

    /** Splits text into pieces of whole sentences: a short first one (quick start), then longer ones. */
    private fun chunk(text: String): List<IntRange> {
        val sentences = sentences(text)
        val result = mutableListOf<IntRange>()
        var startAt = -1
        var endAt = -1
        for (s in sentences) {
            val limit = if (result.isEmpty()) 280 else 1400
            if (startAt >= 0 && s.last - startAt + 1 > limit) {
                result += startAt..endAt
                startAt = -1
            }
            if (startAt < 0) startAt = s.first
            endAt = s.last
        }
        if (startAt >= 0) result += startAt..endAt
        // A single very long sentence is cut at spaces so Google accepts it.
        return result.flatMap { r -> splitLong(text, r, 1800) }
    }

    private fun sentences(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        val boundary = Regex("""(?<=[.!?…:;])["'”’»)]*\s+""")
        var start = 0
        boundary.findAll(text).forEach { m ->
            val end = m.range.first - 1
            if (end >= start) out += start..end
            start = m.range.last + 1
        }
        if (start < text.length) out += start until text.length
        return out.filter { r -> text.substring(r).isNotBlank() }
    }

    private fun splitLong(text: String, range: IntRange, max: Int): List<IntRange> {
        if (range.last - range.first + 1 <= max) return listOf(range)
        val out = mutableListOf<IntRange>()
        var s = range.first
        while (range.last - s + 1 > max) {
            val cut = text.lastIndexOf(' ', s + max).takeIf { it > s } ?: (s + max)
            out += s until cut
            s = cut
        }
        out += s..range.last
        return out
    }

    // ---- Google voices ----

    /** Gets speech for [piece] from the cache or Google; null means use the phone's voice. */
    private suspend fun fetch(piece: String, languageCode: String): File? {
        val config = settingsStore.readAloudConfig.first()
        val key = config.apiKey ?: return null
        if (System.currentTimeMillis() < offlineUntil) return null
        val usage = settingsStore.readAloudUsage.first()
        val chars = piece.length.toLong()

        val chirpVoice = "$languageCode-Chirp3-HD-${config.voiceName}"
        if (usage.chirp + chars <= config.chirpLimit) {
            synth(key, piece, languageCode, chirpVoice, VoiceTier.Chirp)?.let { return it }
            if (System.currentTimeMillis() < offlineUntil || _notice.value == BAD_KEY) return null
        }
        if (usage.wavenet + chars <= config.wavenetLimit) {
            val wavenet = wavenetVoiceFor(key, languageCode) ?: return null
            synth(key, piece, languageCode, wavenet, VoiceTier.WaveNet)?.let { return it }
        }
        return null
    }

    private suspend fun synth(key: String, piece: String, languageCode: String, voice: String, tier: VoiceTier): File? {
        val file = cacheFile(voice, piece)
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            _tier.value = tier
            return file
        }
        return try {
            val audio = google.synthesize(key, piece, languageCode, voice)
            settingsStore.addReadAloudUsage(chirp = tier == VoiceTier.Chirp, characters = piece.length.toLong())
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(audio)
                tmp.renameTo(file)
                trimCache()
            }
            _notice.value = null
            _tier.value = tier
            file
        } catch (e: GoogleTtsException.Offline) {
            offlineUntil = System.currentTimeMillis() + 60_000
            _notice.value = "Offline – using the phone's voice."
            null
        } catch (e: GoogleTtsException.BadKey) {
            _notice.value = BAD_KEY
            null
        } catch (e: GoogleTtsException) {
            if (e !is GoogleTtsException.VoiceUnavailable) _notice.value = e.message
            null
        }
    }

    private suspend fun wavenetVoiceFor(key: String, languageCode: String): String? {
        if (languageCode in wavenetVoices) return wavenetVoices[languageCode]
        val voices: List<GoogleVoice> = try {
            google.voices(key, languageCode)
        } catch (e: GoogleTtsException.Offline) {
            offlineUntil = System.currentTimeMillis() + 60_000
            return null
        } catch (e: GoogleTtsException) {
            return null
        }
        val pick = voices.firstOrNull { it.type == "WaveNet" && languageCode in it.languageCodes }?.name
        wavenetVoices[languageCode] = pick
        return pick
    }

    private fun cacheFile(voice: String, text: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest("$voice|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "read-aloud/$hash.mp3")
    }

    private fun trimCache() {
        val files = File(context.cacheDir, "read-aloud").listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    // ---- Playing ----

    /** Plays one piece; reports each sentence as it's (roughly) reached. False if the file won't play. */
    private suspend fun playFile(id: TtsEngine.RequestId, file: File, text: String, range: IntRange, seekMillis: Int): Boolean {
        val mp = MediaPlayer()
        val prepared = runCatching {
            mp.setDataSource(file.path)
            mp.prepare()
            if (seekMillis > 0) mp.seekTo(seekMillis)
        }.isSuccess
        if (!prepared) {
            mp.release()
            file.delete()
            return false
        }
        player = mp
        // Spread the sentences over the audio by their length, to move the highlight along.
        val sentences = sentences(text.substring(range)).map { (it.first + range.first)..(it.last + range.first) }
        val total = (range.last - range.first + 1).coerceAtLeast(1)
        val duration = mp.duration.coerceAtLeast(1)
        val startsAt = sentences.map { s -> (s.first - range.first).toLong() * duration / total }

        var reported = -1
        val ticker = scope.launch {
            while (isActive) {
                val pos = runCatching { mp.currentPosition }.getOrNull() ?: break
                val index = startsAt.indexOfLast { it <= pos }
                if (index >= 0 && index != reported) {
                    reported = index
                    listener?.onRange(id, sentences[index])
                }
                delay(120)
            }
        }
        return try {
            suspendCancellableCoroutine { cont ->
                onPlayerFinished = { ok -> if (cont.isActive) cont.resume(ok) }
                mp.setOnCompletionListener { onPlayerFinished?.invoke(true) }
                mp.setOnErrorListener { _, _, _ -> onPlayerFinished?.invoke(false); true }
                runCatching {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed())
                    mp.start()
                }.onFailure { cont.resume(false) }
            }
        } finally {
            ticker.cancel()
            if (player === mp) {
                player = null
                onPlayerFinished = null
                runCatching { mp.release() }
            }
        }
    }

    private suspend fun speakWithPhone(id: TtsEngine.RequestId, text: String, range: IntRange, lang: Language): Boolean {
        val tts = phone ?: return false
        val utteranceId = UUID.randomUUID().toString()
        runCatching { tts.setLanguage(lang.locale) }
        tts.setSpeechRate(speed())
        return suspendCancellableCoroutine { cont ->
            phoneRange = { r -> listener?.onRange(id, (r.first + range.first)..(r.last + range.first)) }
            phoneWaiters[utteranceId] = { ok -> if (cont.isActive) cont.resume(ok) }
            cont.invokeOnCancellation { main.post { phoneWaiters.remove(utteranceId); runCatching { tts.stop() } } }
            val result = tts.speak(text.substring(range), TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                phoneWaiters.remove(utteranceId)
                cont.resume(false)
            }
        }
    }

    companion object {
        const val BAD_KEY = "Google didn't accept the key – using the phone's voice. Check Settings → Read aloud."
        private const val CACHE_BYTES = 100L * 1024 * 1024

        /** Google's language code for a book language, e.g. "hu" → "hu-HU", "en" → "en-GB". */
        fun googleLanguage(language: Language): String {
            val locale = language.locale
            val lang = locale.language.ifEmpty { "en" }
            if (locale.country.isNotEmpty()) return "$lang-${locale.country}"
            return when (lang) {
                "en" -> "en-GB"
                "zh" -> "cmn-CN"
                "ja" -> "ja-JP"
                "ko" -> "ko-KR"
                "da" -> "da-DK"
                "sv" -> "sv-SE"
                "nb", "no" -> "nb-NO"
                "cs" -> "cs-CZ"
                "el" -> "el-GR"
                "uk" -> "uk-UA"
                "he", "iw" -> "he-IL"
                "hi" -> "hi-IN"
                "vi" -> "vi-VN"
                "ar" -> "ar-XA"
                "fil" -> "fil-PH"
                "ms" -> "ms-MY"
                "et" -> "et-EE"
                "sl" -> "sl-SI"
                "sr" -> "sr-RS"
                "ca" -> "ca-ES"
                "gl" -> "gl-ES"
                "eu" -> "eu-ES"
                else -> "$lang-${lang.uppercase(Locale.ROOT)}"
            }
        }

        suspend fun create(
            context: Context,
            settingsStore: SettingsStore,
            google: GoogleTts,
            defaultLanguage: Language?,
            initialPreferences: ReadAloudPreferences,
        ): ReadAloudEngine {
            val phone = startPhoneVoice(context)
            return ReadAloudEngine(context, settingsStore, google, phone, defaultLanguage, initialPreferences)
        }

        private suspend fun startPhoneVoice(context: Context): TextToSpeech? {
            var tts: TextToSpeech? = null
            val ok = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    tts = TextToSpeech(context) { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }
                }
            } ?: false
            if (!ok) runCatching { tts?.shutdown() }
            return tts.takeIf { ok }
        }
    }
}

/** Gives Readium EasyReader's voice. Keeps hold of the engine so the player bar can show which voice is in use. */
class ReadAloudEngineProvider(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val google: GoogleTts,
) : TtsEngineProvider<ReadAloudSettings, ReadAloudPreferences, ReadAloudPreferencesEditor, ReadAloudError, ReadAloudVoice> {

    private val _engine = MutableStateFlow<ReadAloudEngine?>(null)
    val engine: StateFlow<ReadAloudEngine?> = _engine.asStateFlow()

    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: ReadAloudPreferences,
    ): Try<TtsEngine<ReadAloudSettings, ReadAloudPreferences, ReadAloudError, ReadAloudVoice>, Error> {
        val engine = ReadAloudEngine.create(
            context, settingsStore, google, publication.metadata.language, initialPreferences,
        )
        _engine.value = engine
        return Try.success(engine)
    }

    override fun createPreferencesEditor(publication: Publication, initialPreferences: ReadAloudPreferences) =
        ReadAloudPreferencesEditor(initialPreferences)

    override fun createEmptyPreferences() = ReadAloudPreferences()

    override fun getPlaybackParameters(settings: ReadAloudSettings) =
        PlaybackParameters(settings.speed.toFloat(), 1f)

    override fun updatePlaybackParameters(previousPreferences: ReadAloudPreferences, playbackParameters: PlaybackParameters) =
        previousPreferences.copy(speed = playbackParameters.speed.toDouble())

    override fun mapEngineError(error: ReadAloudError) =
        PlaybackException(error.message, null, PlaybackException.ERROR_CODE_UNSPECIFIED)
}
