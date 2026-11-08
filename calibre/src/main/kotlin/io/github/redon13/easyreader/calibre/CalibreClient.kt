package io.github.redon13.easyreader.calibre

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Talks to one Calibre content server, using the same JSON endpoints as
 * Calibre's own web interface. Every function may throw [CalibreException].
 */
class CalibreClient(
    val serverUrl: HttpUrl,
    username: String,
    password: String,
    baseClient: OkHttpClient = OkHttpClient(),
) {
    /** Logged-in HTTP client. Also used by the app to load cover images. */
    val httpClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(CalibreAuthInterceptor(username, password))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Lists the libraries on the server. Also a good test that the login works. */
    suspend fun libraries(): CalibreLibraries {
        val obj = getJson(url("ajax", "library-info")).jsonObject
        val map = obj["library_map"]?.jsonObject.orEmpty()
            .mapValues { (_, v) -> v.stringOrNull() ?: "" }
        val default = obj["default_library"].stringOrNull() ?: map.keys.firstOrNull()
            ?: throw CalibreException.UnexpectedResponse(null)
        return CalibreLibraries(map, default)
    }

    /** Checks the library has EasyReader's four custom columns, with the right types. */
    suspend fun checkColumns(libraryId: String): ColumnCheck {
        val url = url("interface-data", "books-init").newBuilder()
            .addQueryParameter("library_id", libraryId)
            .addQueryParameter("num", "0")
            .build()
        val fields = getJson(url).jsonObject["field_metadata"]?.jsonObject
            ?: throw CalibreException.UnexpectedResponse(null)
        val missing = mutableListOf<RequiredColumn>()
        val wrong = mutableListOf<RequiredColumn>()
        for (col in RequiredColumn.entries) {
            val meta = fields[col.lookupName] as? JsonObject
            if (meta == null) {
                missing += col
                continue
            }
            val datatype = meta["datatype"].stringOrNull()
            val isMultiple = (meta["is_multiple"] as? JsonObject)?.isNotEmpty() == true
            if (datatype != col.datatype || isMultiple != col.multiple) wrong += col
        }
        return ColumnCheck(missing, wrong)
    }

    /** Ids of every book in the library, newest first. */
    suspend fun allBookIds(libraryId: String): List<Int> {
        val url = url("ajax", "search", libraryId).newBuilder()
            .addQueryParameter("query", "")
            .addQueryParameter("num", Int.MAX_VALUE.toString())
            .addQueryParameter("sort", "timestamp")
            .addQueryParameter("sort_order", "desc")
            .build()
        val ids = getJson(url).jsonObject["book_ids"]?.jsonArray
            ?: throw CalibreException.UnexpectedResponse(null)
        return ids.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
    }

    /** Full details for the given books. Books that no longer exist are left out. */
    suspend fun books(libraryId: String, ids: List<Int>): List<CalibreBook> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<CalibreBook>()
        for (chunk in ids.chunked(BOOKS_PER_REQUEST)) {
            val url = url("ajax", "books", libraryId).newBuilder()
                .addQueryParameter("ids", chunk.joinToString(","))
                .addQueryParameter("category_urls", "false")
                .build()
            val obj = getJson(url).jsonObject
            for ((key, value) in obj) {
                val id = key.toIntOrNull() ?: continue
                val book = value as? JsonObject ?: continue
                out += CalibreBookParser.parse(id, book)
            }
        }
        return out
    }

    /**
     * Address of a book's cover thumbnail. [version] (e.g. the last-modified
     * time) makes the address change when the cover changes, so old copies
     * aren't shown.
     */
    fun thumbnailUrl(libraryId: String, bookId: Int, version: String? = null): String =
        url("get", "thumb", bookId.toString(), libraryId).newBuilder()
            .addQueryParameter("sz", "300x450")
            .apply { if (version != null) addQueryParameter("v", version) }
            .build().toString()

    // ----------------------------------------------------------------------

    private fun url(vararg segments: String): HttpUrl {
        val b = serverUrl.newBuilder()
        // serverUrl always ends in "/", which leaves an empty last segment to replace.
        b.removePathSegment(serverUrl.pathSize - 1)
        segments.forEach { b.addPathSegment(it) }
        return b.build()
    }

    /** Details of a single book, or null if it's no longer in the library. */
    suspend fun book(libraryId: String, id: Int): CalibreBook? = books(libraryId, listOf(id)).firstOrNull()

    /**
     * Saves one custom column's value for a book (null clears it). The
     * Calibre user must be allowed to make changes.
     */
    suspend fun setCustomColumn(libraryId: String, bookId: Int, lookupName: String, value: String?) =
        setCustomColumn(libraryId, bookId, lookupName, value?.let(::JsonPrimitive) ?: JsonNull)

    /** Saves a Yes/No custom column (null clears it). */
    suspend fun setCustomColumn(libraryId: String, bookId: Int, lookupName: String, value: Boolean?) =
        setCustomColumn(libraryId, bookId, lookupName, value?.let(::JsonPrimitive) ?: JsonNull)

    /** Saves a list column such as #er_shelves. */
    suspend fun setCustomColumn(libraryId: String, bookId: Int, lookupName: String, values: List<String>) =
        setCustomColumn(libraryId, bookId, lookupName, JsonArray(values.map(::JsonPrimitive)))

    suspend fun setCustomColumn(libraryId: String, bookId: Int, lookupName: String, value: JsonElement) {
        val url = url("cdb", "set-fields", bookId.toString(), libraryId)
        val payload = buildJsonObject {
            put("changes", buildJsonObject { put(lookupName, value) })
            put("loaded_book_ids", JsonArray(emptyList()))
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        execute(request) { }
    }

    /** Calibre's virtual libraries and saved searches, A to Z. */
    suspend fun smartShelves(libraryId: String): List<SmartShelf> {
        val init = url("interface-data", "books-init").newBuilder()
            .addQueryParameter("library_id", libraryId)
            .addQueryParameter("num", "0")
            .build()
        val virtual = (getJson(init).jsonObject["virtual_libraries"] as? JsonObject).orEmpty().keys
            .map { SmartShelf(it, SmartShelf.Kind.VIRTUAL_LIBRARY) }
        // Saved searches are a Tag browser category, named "search" (hex encoded in the address).
        val category = url("ajax", "category", "736561726368", libraryId).newBuilder()
            .addQueryParameter("num", "10000")
            .build()
        val saved = (getJson(category).jsonObject["items"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("name").stringOrNull() }
            .map { SmartShelf(it, SmartShelf.Kind.SAVED_SEARCH) }
        return (virtual + saved).sortedBy { it.name.lowercase() }
    }

    /** Ids of the books in a virtual library or saved search. */
    suspend fun smartShelfBookIds(libraryId: String, shelf: SmartShelf): List<Int> {
        val b = url("ajax", "search", libraryId).newBuilder()
            .addQueryParameter("num", Int.MAX_VALUE.toString())
        when (shelf.kind) {
            SmartShelf.Kind.VIRTUAL_LIBRARY -> b.addQueryParameter("query", "").addQueryParameter("vl", shelf.name)
            SmartShelf.Kind.SAVED_SEARCH ->
                b.addQueryParameter("query", "search:\"=" + shelf.name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        }
        val ids = getJson(b.build()).jsonObject["book_ids"]?.jsonArray ?: throw CalibreException.UnexpectedResponse(null)
        return ids.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
    }

    /**
     * Downloads one format of a book (e.g. "EPUB") into [destination].
     * [onProgress] gets 0–1, or null while the size is unknown.
     */
    suspend fun downloadFormat(
        libraryId: String,
        bookId: Int,
        format: String,
        destination: File,
        onProgress: (Float?) -> Unit = {},
    ) {
        val url = url("get", format.uppercase(), bookId.toString(), libraryId)
        val partial = File(destination.path + ".part")
        destination.parentFile?.mkdirs()
        try {
            execute(Request.Builder().url(url).build()) { response ->
                val body = response.body ?: throw CalibreException.UnexpectedResponse(null)
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            onProgress(total?.let { done.toFloat() / it })
                        }
                    }
                }
            }
            if (!partial.renameTo(destination)) throw CalibreException.UnexpectedResponse(null)
        } finally {
            partial.delete()
        }
    }

    // ----------------------------------------------------------------------

    private suspend fun getJson(url: HttpUrl): JsonElement {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = execute(request) { it.body?.string() ?: "" }
        return try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw CalibreException.UnexpectedResponse(e)
        }
    }

    /** Sends a request and turns failures into [CalibreException]s the owner can understand. */
    private suspend fun <T> execute(request: Request, read: suspend (Response) -> T): T = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw CalibreException.WrongLogin()
                    response.code == 403 || response.code == 404 -> throw CalibreException.NotAllowed()
                    !response.isSuccessful -> throw CalibreException.ServerError(response.code)
                }
                read(response)
            }
        } catch (e: CalibreException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw CalibreException.CannotReach(e)
        } catch (e: IllegalArgumentException) {
            throw CalibreException.BadAddress()
        }
    }

    companion object {
        private const val BOOKS_PER_REQUEST = 100

        /**
         * Tidies what the owner typed into a proper address: adds http:// if
         * missing and makes sure it ends with "/". Returns null if it can't be used.
         */
        fun normaliseAddress(input: String): HttpUrl? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "http://$s"
            val url = s.toHttpUrlOrNull() ?: return null
            if (url.host.isBlank()) return null
            val path = url.encodedPath.trimEnd('/') + "/"
            return url.newBuilder().encodedPath(path).query(null).fragment(null).build()
        }
    }
}

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
