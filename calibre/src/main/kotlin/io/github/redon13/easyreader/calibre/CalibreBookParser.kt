package io.github.redon13.easyreader.calibre

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** Turns one book from Calibre's /ajax/books response into a [CalibreBook]. */
object CalibreBookParser {

    /** Re-reads a book from the JSON kept in [CalibreBook.rawJson]. */
    fun parse(id: Int, rawJson: String): CalibreBook =
        parse(id, Json.parseToJsonElement(rawJson).jsonObject)

    fun parse(id: Int, o: JsonObject) = CalibreBook(
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
        identifiers = (o["identifiers"] as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> v.stringOrNull()?.let { k to it } }.toMap(),
        customColumns = parseCustomColumns(o["user_metadata"] as? JsonObject),
        rawJson = o.toString(),
    )

    /**
     * Returns the stored book JSON with one custom column's value replaced, so a
     * change shows straight away, before the next sync. Unchanged if the column
     * doesn't exist in this library.
     */
    fun withCustomValue(rawJson: String, lookupName: String, value: JsonElement): String {
        val book = Json.parseToJsonElement(rawJson).jsonObject
        val meta = book["user_metadata"] as? JsonObject ?: return rawJson
        val column = meta[lookupName] as? JsonObject ?: return rawJson
        val newColumn = JsonObject(column + ("#value#" to value))
        val newMeta = JsonObject(meta + (lookupName to newColumn))
        return JsonObject(book + ("user_metadata" to newMeta)).toString()
    }

    private fun parseCustomColumns(meta: JsonObject?): List<CustomColumnValue> {
        if (meta == null) return emptyList()
        return meta.mapNotNull { (lookup, element) ->
            val col = element as? JsonObject ?: return@mapNotNull null
            val datatype = col["datatype"].stringOrNull() ?: return@mapNotNull null
            val value = col["#value#"]
            val values: List<String> = when (datatype) {
                "bool" -> listOfNotNull((value as? JsonPrimitive)?.booleanOrNull?.let { if (it) "Yes" else "No" })
                "int", "float" -> listOfNotNull((value as? JsonPrimitive)?.doubleOrNull?.let(::formatNumber))
                // Calibre stores custom ratings as half-stars (0–10).
                "rating" -> listOfNotNull((value as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { formatNumber(it / 2) })
                "datetime" -> listOfNotNull(value.dateOrNull())
                "series" -> listOfNotNull(value.stringOrNull()?.let { name ->
                    val index = (col["#extra#"] as? JsonPrimitive)?.doubleOrNull
                    if (index != null) "$name #${formatNumber(index)}" else name
                })
                else -> when (value) {
                    is JsonArray -> value.stringList()
                    else -> listOfNotNull(value.stringOrNull())
                }
            }
            CustomColumnValue(
                lookupName = lookup,
                name = col["name"].stringOrNull() ?: lookup,
                datatype = datatype,
                values = values,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun formatNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}

internal fun JsonElement?.stringList(): List<String> =
    (this as? JsonArray)?.mapNotNull { it.stringOrNull() } ?: emptyList()

/** Calibre sends "None" or a year-101 date for "no date". */
internal fun JsonElement?.dateOrNull(): String? =
    stringOrNull()?.takeUnless { it == "None" || it.startsWith("0101-") || it.startsWith("0100-") }
