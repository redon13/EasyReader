package io.github.redon13.easyreader.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A voice offered by Google Cloud Text-to-Speech. */
data class GoogleVoice(val name: String, val languageCodes: List<String>, val gender: String) {
    /** "Chirp3-HD", "Wavenet", "Neural2", "Standard"… taken from the name, e.g. hu-HU-Chirp3-HD-Iapetus. */
    val type: String
        get() = when {
            "Chirp3-HD" in name -> "Chirp 3 HD"
            "Chirp-HD" in name -> "Chirp HD"
            "Wavenet" in name -> "WaveNet"
            "Neural2" in name -> "Neural2"
            "Studio" in name -> "Studio"
            "Standard" in name -> "Standard"
            else -> "Other"
        }

    /** The short name, e.g. "Iapetus" for hu-HU-Chirp3-HD-Iapetus. */
    val shortName: String get() = name.substringAfterLast('-')
}

/** Problems talking to Google, in plain words. */
sealed class GoogleTtsException(message: String) : Exception(message) {
    class Offline : GoogleTtsException("Not connected to the internet.")
    class BadKey : GoogleTtsException("Google didn't accept the key. Check it in Settings → Read aloud.")
    class VoiceUnavailable : GoogleTtsException("That voice isn't available for this language.")
    class Other(code: Int, detail: String?) : GoogleTtsException("Google voice error ($code)${detail?.let { ": $it" } ?: ""}.")
}

/**
 * Google Cloud Text-to-Speech, used for read-aloud. Sends the app's package
 * name and signing fingerprint with each request, so a key restricted to
 * EasyReader works (and is useless anywhere else).
 */
class GoogleTts(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The app's signing fingerprint, e.g. "AB:CD:…", as shown when restricting the key. */
    val appFingerprint: String? by lazy {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return@runCatching null
            MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }

    /** Voices for a language code such as "hu-HU" (or all languages if null). */
    suspend fun voices(key: String, languageCode: String?): List<GoogleVoice> {
        val url = BASE + "voices" + (languageCode?.let { "?languageCode=$it" } ?: "")
        val body = send(Request.Builder().url(url).withKey(key).get().build())
        val voices = (json.parseToJsonElement(body) as? JsonObject)?.get("voices") as? JsonArray ?: return emptyList()
        return voices.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val langs = (o["languageCodes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            GoogleVoice(name, langs, (o["ssmlGender"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }.sortedBy { it.name }
    }

    /** Speech for [text] as MP3. */
    suspend fun synthesize(key: String, text: String, languageCode: String, voiceName: String): ByteArray {
        val payload = buildJsonObject {
            put("input", buildJsonObject { put("text", text) })
            put("voice", buildJsonObject {
                put("languageCode", languageCode)
                put("name", voiceName)
            })
            put("audioConfig", buildJsonObject { put("audioEncoding", "MP3") })
        }
        val request = Request.Builder()
            .url(BASE + "text:synthesize")
            .withKey(key)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = send(request)
        val audio = ((json.parseToJsonElement(body) as? JsonObject)?.get("audioContent") as? JsonPrimitive)?.contentOrNull
            ?: throw GoogleTtsException.Other(200, "no audio returned")
        return Base64.decode(audio, Base64.DEFAULT)
    }

    private fun Request.Builder.withKey(key: String): Request.Builder {
        header("X-Goog-Api-Key", key)
        header("X-Android-Package", context.packageName)
        appFingerprint?.let { header("X-Android-Cert", it.replace(":", "")) }
        return this
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return@use text
                val message = runCatching {
                    ((json.parseToJsonElement(text) as JsonObject)["error"] as JsonObject)["message"]
                        .let { (it as? JsonPrimitive)?.contentOrNull }
                }.getOrNull()
                when {
                    response.code == 403 || (response.code == 400 && message?.contains("API key", true) == true) ->
                        throw GoogleTtsException.BadKey()
                    response.code == 400 && message?.contains("voice", true) == true ->
                        throw GoogleTtsException.VoiceUnavailable()
                    else -> throw GoogleTtsException.Other(response.code, message)
                }
            }
        } catch (e: GoogleTtsException) {
            throw e
        } catch (e: IOException) {
            throw GoogleTtsException.Offline()
        }
    }

    private companion object {
        const val BASE = "https://texttospeech.googleapis.com/v1/"
    }
}
