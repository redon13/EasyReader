package io.github.redon13.easyreader.calibre

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalibreClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client(prefix: String = "/") =
        CalibreClient(server.url(prefix), "me", "secret")

    @Test fun `normalises typed addresses`() {
        assertEquals("http://my-pc:8080/", CalibreClient.normaliseAddress(" my-pc:8080 ").toString())
        assertEquals("https://x.example/calibre/", CalibreClient.normaliseAddress("https://x.example/calibre").toString())
        assertNull(CalibreClient.normaliseAddress(""))
        assertNull(CalibreClient.normaliseAddress("ftp://x"))
    }

    @Test fun `answers a digest challenge then reuses it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401)
            .addHeader("WWW-Authenticate", "Digest realm=\"calibre\", nonce=\"abc\", algorithm=\"MD5\", qop=\"auth\""))
        repeat(2) {
            server.enqueue(MockResponse().setBody("""{"library_map":{"lib":"My Books"},"default_library":"lib"}"""))
        }
        val c = client()
        val libs = c.libraries()
        assertEquals(mapOf("lib" to "My Books"), libs.libraries)
        c.libraries()

        assertNull(server.takeRequest().getHeader("Authorization"))
        val second = server.takeRequest().getHeader("Authorization")!!
        assertTrue(second, second.startsWith("Digest username=\"me\""))
        assertTrue(second, second.contains("uri=\"/ajax/library-info\""))
        assertTrue(second, second.contains("nc=00000001"))
        // Third request goes straight through with the remembered challenge.
        assertTrue(server.takeRequest().getHeader("Authorization")!!.contains("nc=00000002"))
    }

    @Test fun `wrong password is reported`() = runBlocking {
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(401)
                .addHeader("WWW-Authenticate", "Digest realm=\"calibre\", nonce=\"abc\", qop=\"auth\""))
        }
        try {
            client().libraries(); error("should fail")
        } catch (e: CalibreException.WrongLogin) { /* expected */ }
    }

    @Test fun `unreachable server is reported`() = runBlocking {
        val url = server.url("/"); server.shutdown()
        try {
            CalibreClient(url, "a", "b").libraries(); error("should fail")
        } catch (e: CalibreException.CannotReach) { /* expected */ }
    }

    @Test fun `parses books and skips missing ones`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"1": {"title": "Emma", "authors": ["Jane Austen"], "series": null, "series_index": 1.0,
                   "rating": 4.0, "formats": ["epub", "pdf"], "pubdate": "None",
                   "timestamp": "2026-11-07T14:46:35+00:00", "tags": ["classic"]},
             "2": null}
        """.trimIndent()))
        val books = client("/calibre/").books("lib", listOf(1, 2))
        assertEquals(1, books.size)
        val b = books[0]
        assertEquals("Emma", b.title)
        assertEquals(listOf("Jane Austen"), b.authors)
        assertEquals(listOf("EPUB", "PDF"), b.formats)
        assertNull(b.series)
        assertNull(b.pubdate)
        assertEquals(4.0, b.rating!!, 0.0)
        assertEquals("/calibre/ajax/books/lib?ids=1%2C2&category_urls=false", server.takeRequest().path)
    }

    @Test fun `finds missing and wrongly typed columns`() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"field_metadata": {
              "title": {"datatype": "text", "is_multiple": {}},
              "#er_shelves": {"datatype": "text", "is_multiple": {"cache_to_list": "|"}},
              "#er_favourite": {"datatype": "text", "is_multiple": {}},
              "#er_position": {"datatype": "comments", "is_multiple": {}}
            }}
        """.trimIndent()))
        val check = client().checkColumns("lib")
        assertEquals(listOf(RequiredColumn.FINISHED), check.missing)
        assertEquals(listOf(RequiredColumn.FAVOURITE), check.wrongType)
    }

    @Test fun `smart shelves work without saved searches`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"virtual_libraries": {"Sci-fi": "tags:scifi"}}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("Category 'search' not found"))
        val shelves = client().smartShelves("lib")
        assertEquals(listOf(SmartShelf("Sci-fi", SmartShelf.Kind.VIRTUAL_LIBRARY)), shelves)
    }
}
