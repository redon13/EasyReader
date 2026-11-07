package io.github.redon13.easyreader.data.browse

import io.github.redon13.easyreader.calibre.CalibreBook
import java.text.Normalizer
import java.util.Locale

enum class SortField(val label: String, val isDate: Boolean) {
    DATE_ADDED("Date added", true),
    LAST_MODIFIED("Last modified", true),
    TITLE("Title", false),
    AUTHOR("Author", false),
    PUBLISHER("Publisher", false),
    SERIES("Series", false),
}

data class SortOrder(
    val field: SortField = SortField.DATE_ADDED,
    val descending: Boolean = true,
) {
    val label: String
        get() = "${this.field.label}, ${directionLabel(this.field, descending)}"

    companion object {
        fun directionLabel(field: SortField, descending: Boolean): String = when {
            field.isDate && descending -> "Newest first"
            field.isDate -> "Oldest first"
            descending -> "Z to A"
            else -> "A to Z"
        }
    }
}

enum class FilterCategory(val label: String) {
    AUTHORS("Authors"),
    SERIES("Series"),
    LANGUAGES("Languages"),
    TAGS("Tags"),
    PUBLISHERS("Publishers"),
    FORMATS("Formats");

    fun valuesOf(b: CalibreBook): List<String> = when (this) {
        AUTHORS -> b.authors
        SERIES -> listOfNotNull(b.series)
        LANGUAGES -> b.languages
        TAGS -> b.tags
        PUBLISHERS -> listOfNotNull(b.publisher)
        FORMATS -> b.formats
    }
}

/** What the owner is looking for: search text, at most one filter per category, and the sort order. */
data class BrowseQuery(
    val search: String = "",
    val filters: Map<FilterCategory, String> = emptyMap(),
    val sort: SortOrder = SortOrder(),
) {
    /** True when the library is being narrowed down, rather than showing everything. */
    val isNarrowed: Boolean
        get() = search.isNotBlank() || filters.isNotEmpty()
}

data class CategoryValue(val value: String, val count: Int)

/** Search, filter and sort, all done on the device so it's instant and works offline. */
object Browse {

    fun apply(books: List<CalibreBook>, query: BrowseQuery): List<CalibreBook> =
        sort(books.filter { matches(it, query) }, query.sort)

    fun matches(book: CalibreBook, query: BrowseQuery): Boolean {
        for ((category, value) in query.filters) {
            if (category.valuesOf(book).none { it.equals(value, ignoreCase = true) }) return false
        }
        val terms = normalise(query.search).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = normalise(
            buildList {
                add(book.title)
                addAll(book.authors)
                book.series?.let(::add)
                addAll(book.tags)
                book.publisher?.let(::add)
            }.joinToString("\n")
        )
        // Every word typed must appear somewhere, e.g. "austen emma".
        return terms.all { haystack.contains(it) }
    }

    fun sort(books: List<CalibreBook>, order: SortOrder): List<CalibreBook> {
        val key: (CalibreBook) -> String? = when (order.field) {
            SortField.DATE_ADDED -> { b -> b.timestamp }
            SortField.LAST_MODIFIED -> { b -> b.lastModified }
            SortField.TITLE -> { b -> sortText(b.titleSort ?: b.title) }
            SortField.AUTHOR -> { b -> (b.authorSort ?: b.authors.firstOrNull())?.let(::sortText) }
            SortField.PUBLISHER -> { b -> b.publisher?.let(::sortText) }
            SortField.SERIES -> { b -> b.series?.let(::sortText) }
        }
        val comparator = Comparator<CalibreBook> { a, b ->
            val ka = key(a)
            val kb = key(b)
            // Books without a value (e.g. no series) always go to the end.
            var c = when {
                ka == null && kb == null -> 0
                ka == null -> return@Comparator 1
                kb == null -> return@Comparator -1
                else -> ka.compareTo(kb)
            }
            if (c == 0 && order.field == SortField.SERIES) {
                c = (a.seriesIndex ?: 0.0).compareTo(b.seriesIndex ?: 0.0)
            }
            if (order.descending) c = -c
            if (c == 0) c = sortText(a.titleSort ?: a.title).compareTo(sortText(b.titleSort ?: b.title))
            if (c == 0) c = a.id.compareTo(b.id)
            c
        }
        return books.sortedWith(comparator)
    }

    /** Every value in a category with how many books have it, A to Z. */
    fun values(books: List<CalibreBook>, category: FilterCategory): List<CategoryValue> {
        val counts = LinkedHashMap<String, Int>()
        for (b in books) {
            for (v in category.valuesOf(b).distinct()) counts[v] = (counts[v] ?: 0) + 1
        }
        return counts.map { (v, n) -> CategoryValue(v, n) }.sortedBy { sortText(it.value) }
    }

    /** Lower case, with accents removed, so "Brontë" matches "bronte". */
    fun normalise(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    private fun sortText(s: String) = normalise(s)

    /** Calibre stores languages as three-letter codes ("eng"); this gives "English". */
    fun languageName(code: String): String {
        val two = threeToTwo[code.lowercase()] ?: code
        val name = Locale(two).getDisplayLanguage(Locale.UK)
        return if (name.isBlank() || name.equals(two, ignoreCase = true)) code else name
    }

    private val threeToTwo: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { two ->
            runCatching { Locale(two).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { it to two }
        }.toMap()
    }
}
