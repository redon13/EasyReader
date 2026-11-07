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

    @Test fun `logs in and reads the library`() = runBlocking {
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
    }
}
