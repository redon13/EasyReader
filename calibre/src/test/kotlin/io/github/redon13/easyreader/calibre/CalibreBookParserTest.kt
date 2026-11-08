package io.github.redon13.easyreader.calibre

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses a real /ajax/books response from Calibre 7.6, with one column of every type. */
class CalibreBookParserTest {
    private val all = Json.parseToJsonElement(
        javaClass.getResource("/calibre-books.json")!!.readText()
    ).jsonObject

    private fun book(id: Int) = CalibreBookParser.parse(id, all[id.toString()]!!.jsonObject)
    private fun CalibreBook.col(lookup: String) = customColumn(lookup)!!.values

    @Test fun `reads the standard fields`() {
        val b = book(1)
        assertEquals("Test Book 1", b.title)
        assertEquals(listOf("eng", "fra"), b.languages)
        assertEquals("Penguin", b.publisher)
        assertEquals("1815-12-23T00:00:00+00:00", b.pubdate)
        assertEquals(mapOf("isbn" to "9780141439587"), b.identifiers)
    }

    @Test fun `reads every kind of custom column`() {
        val b = book(1)
        assertEquals(listOf("Holiday", "Classics"), b.col("#er_shelves"))
        assertEquals(listOf("Yes"), b.col("#er_favourite"))
        assertEquals(listOf("No"), b.col("#owned"))
        assertEquals(listOf("321"), b.col("#pages"))
        assertEquals(listOf("9.5"), b.col("#price"))
        assertEquals(listOf("2"), b.col("#myrating"))
        assertEquals(listOf("2024-03-05T00:00:00+00:00"), b.col("#readdate"))
        assertEquals(listOf("Inner[2] #1"), b.col("#subseries"))
        assertEquals(listOf("Happy"), b.col("#mood"))
        assertEquals(listOf("<p>Hello <b>there</b></p>"), b.col("#notes"))
        assertEquals("Date read", b.customColumn("#readdate")!!.name)
    }

    @Test fun `empty custom columns have no values`() {
        val b = book(2)
        assertTrue(b.customColumns.isNotEmpty())
        assertTrue(b.customColumns.all { it.values.isEmpty() })
    }

    @Test fun `round trips through stored JSON`() {
        val b = book(1)
        assertEquals(b, CalibreBookParser.parse(1, b.rawJson))
    }

    @Test fun `updates a stored value`() {
        val b = book(2)
        val raw = CalibreBookParser.withCustomValue(b.rawJson, "#er_favourite", kotlinx.serialization.json.JsonPrimitive(true))
        val raw2 = CalibreBookParser.withCustomValue(raw, "#er_shelves",
            kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("Holiday"))))
        val updated = CalibreBookParser.parse(2, raw2)
        assertTrue(updated.isFavourite)
        assertEquals(listOf("Holiday"), updated.shelves)
        // Unknown columns are left alone.
        assertEquals(raw2, CalibreBookParser.withCustomValue(raw2, "#nope", kotlinx.serialization.json.JsonPrimitive(1)))
    }
}
