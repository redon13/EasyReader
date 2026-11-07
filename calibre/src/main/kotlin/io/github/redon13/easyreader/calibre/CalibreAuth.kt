package io.github.redon13.easyreader.calibre

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Logs in to the Calibre content server on every request.
 *
 * Calibre asks for a username and password using either "Digest" (its default
 * over plain http) or "Basic" (its default over https). This handles both. It
 * remembers the server's last challenge so most requests are answered straight
 * away, without first being turned away.
 */
internal class CalibreAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {

    private val lock = Any()
    private var challenge: Challenge? = null
    private var nonceCount = 0L
    private val random = SecureRandom()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val first = chain.proceed(authorise(original))
        if (first.code != 401) return first

        val newChallenge = Challenge.pick(first.headers("WWW-Authenticate")) ?: return first
        synchronized(lock) {
            challenge = newChallenge
            nonceCount = 0
        }
        first.close()
        // Try once more with the fresh challenge. If that is refused too, the
        // username or password is wrong and the 401 is passed back.
        return chain.proceed(authorise(original))
    }

    private fun authorise(request: Request): Request {
        val header = synchronized(lock) {
            val c = challenge ?: return request
            when (c.scheme) {
                "basic" -> Credentials.basic(username, password, Charsets.UTF_8)
                else -> digestHeader(c, request)
            }
        }
        return request.newBuilder().header("Authorization", header).build()
    }

    /** Must be called while holding [lock]. */
    private fun digestHeader(c: Challenge, request: Request): String {
        val realm = c.params["realm"].orEmpty()
        val nonce = c.params["nonce"].orEmpty()
        val opaque = c.params["opaque"]
        val algorithm = c.params["algorithm"] ?: "MD5"
        val useQop = c.params["qop"]?.split(',')?.map { it.trim().lowercase() }?.contains("auth") == true

        val url = request.url
        val uri = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val cnonce = randomHex(16)
        val nc = String.format("%08x", ++nonceCount)

        var ha1 = md5("$username:$realm:$password")
        if (algorithm.equals("MD5-sess", ignoreCase = true)) ha1 = md5("$ha1:$nonce:$cnonce")
        val ha2 = md5("${request.method}:$uri")
        val response = if (useQop) md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2") else md5("$ha1:$nonce:$ha2")

        return buildString {
            append("Digest username=\"").append(quote(username)).append('"')
            append(", realm=\"").append(quote(realm)).append('"')
            append(", nonce=\"").append(quote(nonce)).append('"')
            append(", uri=\"").append(quote(uri)).append('"')
            append(", algorithm=").append(algorithm)
            append(", response=\"").append(response).append('"')
            if (opaque != null) append(", opaque=\"").append(quote(opaque)).append('"')
            if (useQop) append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append('"')
        }
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun quote(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

/** One "WWW-Authenticate" challenge from the server, e.g. Digest realm="calibre", nonce="…". */
internal data class Challenge(val scheme: String, val params: Map<String, String>) {
    companion object {
        /** Picks Digest if offered, otherwise Basic. */
        fun pick(headers: List<String>): Challenge? {
            val all = headers.mapNotNull { parse(it) }
            return all.firstOrNull { it.scheme == "digest" } ?: all.firstOrNull { it.scheme == "basic" }
        }

        fun parse(header: String): Challenge? {
            val trimmed = header.trim()
            val space = trimmed.indexOf(' ')
            val scheme = (if (space < 0) trimmed else trimmed.substring(0, space)).lowercase()
            if (scheme != "digest" && scheme != "basic") return null
            val rest = if (space < 0) "" else trimmed.substring(space + 1)
            return Challenge(scheme, parseParams(rest))
        }

        private fun parseParams(s: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            var i = 0
            while (i < s.length) {
                while (i < s.length && (s[i] == ' ' || s[i] == ',')) i++
                val eq = s.indexOf('=', i)
                if (eq < 0) break
                val key = s.substring(i, eq).trim().lowercase()
                i = eq + 1
                val value = StringBuilder()
                if (i < s.length && s[i] == '"') {
                    i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length) i++
                        value.append(s[i]); i++
                    }
                    i++ // closing quote
                } else {
                    while (i < s.length && s[i] != ',') { value.append(s[i]); i++ }
                }
                out[key] = value.toString().trim()
            }
            return out
        }
    }
}
