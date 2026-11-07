package io.github.redon13.easyreader.calibre

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingPositionTest {
    private val sample = ReadingPosition(
        locator = """{"href":"chapter1.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.425}}""",
        progress = 0.425,
        timestamp = 1_794_000_000_000,
        device = "Galaxy Tab S9 FE",
        format = "EPUB",
    )

    @Test fun `round trips through the column text`() {
        val text = sample.toJson()
        assertTrue(text, text.contains("\"progress\":42.5"))
        assertTrue(text, text.contains("\"timestamp\":\"2026-11-06T"))
        val back = ReadingPosition.parse(text)!!
        assertEquals(sample.progress, back.progress, 0.0001)
        assertEquals(sample.timestamp, back.timestamp)
        assertEquals(sample.device, back.device)
        assertEquals(sample.format, back.format)
        assertTrue(back.locator!!.contains("chapter1.xhtml"))
    }

    @Test fun `ignores empty or foreign text`() {
        assertNull(ReadingPosition.parse(null))
        assertNull(ReadingPosition.parse(""))
        assertNull(ReadingPosition.parse("<p>not json</p>"))
    }

    @Test fun `newest wins`() {
        val older = sample.copy(timestamp = sample.timestamp - 1)
        assertTrue(sample.isNewerThan(older))
        assertFalse(older.isNewerThan(sample))
        assertTrue(sample.isNewerThan(null))
    }

    @Test fun `saves a column and downloads a book`(): Unit = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("EPUBDATA"))
            val client = CalibreClient(server.url("/"), "me", "pw")

            client.setCustomColumn("lib", 7, "#er_position", sample.toJson())
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/cdb/set-fields/7/lib", post.path)
            val body = post.body.readUtf8()
            assertTrue(body, body.startsWith("{\"changes\":{\"#er_position\":\"{"))

            val dest = File.createTempFile("book", ".epub").apply { delete() }
            val progress = mutableListOf<Float?>()
            client.downloadFormat("lib", 7, "epub", dest) { progress += it }
            assertEquals("/get/EPUB/7/lib", server.takeRequest().path)
            assertEquals("EPUBDATA", dest.readText())
            assertEquals(1f, progress.last())
            assertFalse(File(dest.path + ".part").exists())
            dest.delete()
        } finally {
            server.shutdown()
        }
    }
}
