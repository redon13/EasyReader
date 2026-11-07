package io.github.redon13.easyreader.calibre

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs against a real Calibre content server, only when these are set:
 * CALIBRE_TEST_URL, CALIBRE_TEST_USER, CALIBRE_TEST_PASSWORD.
 * Skipped otherwise (for example on GitHub's build machines).
 */
class LiveServerTest {
    private val url = System.getenv("CALIBRE_TEST_URL")

    @Test fun `logs in and reads the library`(): Unit = runBlocking {
        assumeTrue(url != null)
        val client = CalibreClient(
            CalibreClient.normaliseAddress(url!!)!!,
            System.getenv("CALIBRE_TEST_USER"),
            System.getenv("CALIBRE_TEST_PASSWORD"),
        )
        val libs = client.libraries()
        println("Libraries: $libs")
        val lib = libs.defaultLibraryId
        println("Columns: ${client.checkColumns(lib)}")
        val ids = client.allBookIds(lib)
        println("Book ids: $ids")
        val books = client.books(lib, ids)
        books.forEach { println(" - ${it.id}: ${it.title} by ${it.authors} (${it.series} ${it.seriesIndex})") }
        assertTrue(books.size == ids.size)

        val thumb = client.httpClient.newCall(
            okhttp3.Request.Builder().url(client.thumbnailUrl(lib, ids.first(), books.first().lastModified)).build()
        ).execute()
        println("Thumbnail: ${thumb.code} ${thumb.header("Content-Type")}")
        assertTrue(thumb.isSuccessful)
        thumb.close()

        // Save a reading position, read it back, then put the old value back.
        val first = books.first()
        val before = first.customColumn(ReadingPosition.COLUMN)?.values?.firstOrNull()
        val pos = ReadingPosition("""{"href":"a.html"}""", 0.5, System.currentTimeMillis(), "Test", "EPUB")
        client.setCustomColumn(lib, first.id, ReadingPosition.COLUMN, pos.toJson())
        val saved = client.book(lib, first.id)!!.serverPosition
        println("Saved position: $saved")
        assertTrue(saved!!.timestamp == pos.timestamp)
        client.setCustomColumn(lib, first.id, ReadingPosition.COLUMN, before)

        // Download the first book that has a file.
        books.firstOrNull { it.formats.isNotEmpty() }?.let { b ->
            val dest = java.io.File.createTempFile("live", "." + b.formats.first().lowercase())
            client.downloadFormat(lib, b.id, b.formats.first(), dest)
            println("Downloaded ${b.formats.first()}: ${dest.length()} bytes")
            assertTrue(dest.length() > 0)
            dest.delete()
        }
    }
}
