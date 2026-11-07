package io.github.redon13.easyreader.calibre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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
                out += parseBook(id, book)
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

    private suspend fun getJson(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw CalibreException.WrongLogin()
                    response.code == 403 || response.code == 404 -> throw CalibreException.NotAllowed()
                    !response.isSuccessful -> throw CalibreException.ServerError(response.code)
                }
                response.body?.string() ?: ""
            }
        } catch (e: CalibreException) {
            throw e
        } catch (e: IOException) {
            throw CalibreException.CannotReach(e)
        } catch (e: IllegalArgumentException) {
            throw CalibreException.BadAddress()
        }
        try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw CalibreException.UnexpectedResponse(e)
        }
    }

    private fun parseBook(id: Int, o: JsonObject) = CalibreBook(
        id = id,
        title = o["title"].stringOrNull() ?: "Untitled",
        titleSort = o["title_sort"].stringOrNull(),
        authors = o["authors"].stringList(),
        authorSort = o["author_sort"].stringOrNull(),
        series = o["series"].stringOrNull(),
        seriesIndex = (o["series_index"] as? JsonPrimitive)?.doubleOrNull,
        tags = o["tags"].stringList(),
        languages = o["languages"].stringList(),
        publisher = o["publisher"].stringOrNull(),
        rating = (o["rating"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 },
        formats = o["formats"].stringList().map { it.uppercase() },
        timestamp = o["timestamp"].dateOrNull(),
        lastModified = o["last_modified"].dateOrNull(),
        pubdate = o["pubdate"].dateOrNull(),
        comments = o["comments"].stringOrNull(),
        rawJson = o.toString(),
    )

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

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.stringList(): List<String> =
    (this as? JsonArray)?.mapNotNull { it.stringOrNull() } ?: emptyList()

/** Calibre sends "None" or a year-101 date for "no date". */
private fun JsonElement?.dateOrNull(): String? =
    stringOrNull()?.takeUnless { it == "None" || it.startsWith("0101-") || it.startsWith("0100-") }
