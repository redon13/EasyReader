package io.github.redon13.easyreader.data.browse

import io.github.redon13.easyreader.calibre.CalibreBook
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseTest {
    private fun book(
        id: Int,
        title: String,
        authors: List<String> = emptyList(),
        series: String? = null,
        index: Double? = null,
        tags: List<String> = emptyList(),
        publisher: String? = null,
        added: String? = null,
        formats: List<String> = listOf("EPUB"),
    ) = CalibreBook(
        id = id, title = title, titleSort = null, authors = authors, authorSort = null,
        series = series, seriesIndex = index, tags = tags, languages = listOf("eng"),
        publisher = publisher, rating = null, formats = formats, timestamp = added,
        lastModified = added, pubdate = null, comments = null, rawJson = "{}",
    )

    private val emma = book(1, "Emma", listOf("Jane Austen"), tags = listOf("Classic"), publisher = "Penguin", added = "2024-01-01T00:00:00+00:00")
    private val jane = book(2, "Jane Eyre", listOf("Charlotte Brontë"), tags = listOf("Classic", "Gothic"), added = "2025-01-01T00:00:00+00:00", formats = listOf("PDF"))
    private val dune2 = book(3, "Dune Messiah", listOf("Frank Herbert"), series = "Dune", index = 2.0, added = "2023-01-01T00:00:00+00:00")
    private val dune1 = book(4, "Dune", listOf("Frank Herbert"), series = "Dune", index = 1.0, added = "2022-01-01T00:00:00+00:00")
    private val all = listOf(emma, jane, dune2, dune1)

    private fun ids(q: BrowseQuery) = Browse.apply(all, q).map { it.id }

    @Test fun `search matches every word across fields, ignoring accents`() {
        assertEquals(listOf(2), ids(BrowseQuery(search = "bronte")))
        assertEquals(listOf(1), ids(BrowseQuery(search = "austen EMMA")))
        assertEquals(listOf(2, 1), ids(BrowseQuery(search = "classic")))
        assertEquals(listOf(1, 2), ids(BrowseQuery(search = "jane")).sorted())
        assertEquals(emptyList<Int>(), ids(BrowseQuery(search = "austen dune")))
    }

    @Test fun `filters combine across categories`() {
        val q = BrowseQuery(filters = mapOf(FilterCategory.TAGS to "Classic", FilterCategory.FORMATS to "PDF"))
        assertEquals(listOf(2), ids(q))
        assertEquals(listOf(3, 4), ids(BrowseQuery(filters = mapOf(FilterCategory.AUTHORS to "Frank Herbert"))))
    }

    @Test fun `sorts by date, title and series`() {
        assertEquals(listOf(2, 1, 3, 4), ids(BrowseQuery()))
        assertEquals(listOf(4, 3, 1, 2), ids(BrowseQuery(sort = SortOrder(SortField.DATE_ADDED, descending = false))))
        assertEquals(listOf(4, 3, 1, 2), ids(BrowseQuery(sort = SortOrder(SortField.TITLE, descending = false))))
        // Books in a series come first, in series order; the rest follow by title.
        assertEquals(listOf(4, 3, 1, 2), ids(BrowseQuery(sort = SortOrder(SortField.SERIES, descending = false))))
        assertEquals(listOf(3, 4, 1, 2), ids(BrowseQuery(sort = SortOrder(SortField.SERIES, descending = true))))
    }

    @Test fun `counts values in a category`() {
        assertEquals(
            listOf(CategoryValue("Classic", 2), CategoryValue("Gothic", 1)),
            Browse.values(all, FilterCategory.TAGS),
        )
    }
}

class LanguageNameTest {
    @Test fun `turns codes into names`() {
        assertEquals("English", Browse.languageName("eng"))
        assertEquals("French", Browse.languageName("fra"))
        assertEquals("xyz", Browse.languageName("xyz"))
    }
}
