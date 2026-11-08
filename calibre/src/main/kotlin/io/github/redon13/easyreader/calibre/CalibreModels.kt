package io.github.redon13.easyreader.calibre

/** The libraries on the server: id → display name, plus which one is the default. */
data class CalibreLibraries(
    val libraries: Map<String, String>,
    val defaultLibraryId: String,
)

/**
 * One book as Calibre describes it. The commonly used fields are picked out;
 * [rawJson] keeps everything else (custom columns, identifiers, format details…)
 * so later stages can use it without changing what is stored.
 */
data class CalibreBook(
    val id: Int,
    val title: String,
    val titleSort: String?,
    val authors: List<String>,
    val authorSort: String?,
    val series: String?,
    val seriesIndex: Double?,
    val tags: List<String>,
    val languages: List<String>,
    val publisher: String?,
    /** 0–5 stars. */
    val rating: Double?,
    val formats: List<String>,
    /** ISO date/time the book was added to Calibre. */
    val timestamp: String?,
    val lastModified: String?,
    val pubdate: String?,
    /** HTML description. */
    val comments: String?,
    /** e.g. "isbn" → "9780141439587". */
    val identifiers: Map<String, String> = emptyMap(),
    /** Every custom column, including EasyReader's own (#er_…), sorted by name. */
    val customColumns: List<CustomColumnValue> = emptyList(),
    val rawJson: String,
) {
    fun customColumn(lookupName: String): CustomColumnValue? = customColumns.firstOrNull { it.lookupName == lookupName }
}

/** One custom column's value for a book. */
data class CustomColumnValue(
    /** e.g. "#er_shelves". */
    val lookupName: String,
    /** The column heading shown in Calibre. */
    val name: String,
    /** Calibre's type: text, comments, bool, int, float, rating, datetime, series, enumeration, composite. */
    val datatype: String,
    /**
     * Ready to show: Yes/No for bool, stars for rating, "Name #2" for series.
     * Dates are left as ISO date/time text for the app to format. Empty if no value.
     */
    val values: List<String>,
)

/** A custom column EasyReader needs in Calibre. */
enum class RequiredColumn(
    val lookupName: String,
    val heading: String,
    /** Calibre's internal type name. */
    val datatype: String,
    val multiple: Boolean,
    /** How the type is named in Calibre's "Add custom column" screen. */
    val calibreTypeName: String,
) {
    SHELVES("#er_shelves", "Shelves", "text", true, "Comma separated text, like tags, shown in the Tag browser"),
    FAVOURITE("#er_favourite", "Favourite", "bool", false, "Yes/No"),
    FINISHED("#er_finished", "Finished", "bool", false, "Yes/No"),
    POSITION("#er_position", "Reading position", "comments", false, "Long text, like comments, not shown in the Tag browser"),
}

/** Result of checking the library for EasyReader's four custom columns. */
data class ColumnCheck(
    val missing: List<RequiredColumn>,
    /** Columns that exist but were created with the wrong type. */
    val wrongType: List<RequiredColumn>,
) {
    val allPresent get() = missing.isEmpty() && wrongType.isEmpty()
}

/** Something went wrong talking to the server. [message] is plain British English for the owner. */
sealed class CalibreException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class BadAddress : CalibreException("That server address doesn't look right. It should be like http://my-pc:8080")
    class CannotReach(cause: Throwable?) :
        CalibreException("Can't reach the server. Check the address, that Tailscale is on, and that Calibre is running on the PC.", cause)
    class WrongLogin : CalibreException("Wrong username or password.")
    class NotAllowed : CalibreException("The server refused access. Check the user's permissions in Calibre, and that the library still exists.")
    class ServerError(code: Int) : CalibreException("The server had a problem (error $code). Try again in a moment.")
    class UnexpectedResponse(cause: Throwable?) :
        CalibreException("The server sent something unexpected. Is this address really Calibre's content server?", cause)
}

/** A Calibre virtual library or saved search, shown in EasyReader as a smart shelf (view only). */
data class SmartShelf(val name: String, val kind: Kind) {
    enum class Kind { VIRTUAL_LIBRARY, SAVED_SEARCH }
}

/** The shelves this book is on (from #er_shelves, as of the last sync). */
val CalibreBook.shelves: List<String>
    get() = customColumn(RequiredColumn.SHELVES.lookupName)?.values.orEmpty()

/** Whether the book is a favourite (#er_favourite). */
val CalibreBook.isFavourite: Boolean
    get() = customColumn(RequiredColumn.FAVOURITE.lookupName)?.values?.firstOrNull() == "Yes"

/** Whether the book is finished (#er_finished). */
val CalibreBook.isFinished: Boolean
    get() = customColumn(RequiredColumn.FINISHED.lookupName)?.values?.firstOrNull() == "Yes"

/** What happened when a book file was sent to Calibre. */
sealed interface UploadResult {
    val title: String
    val authors: List<String>

    data class Added(val bookId: Int, override val title: String, override val authors: List<String>) : UploadResult

    /** Calibre already had this book, so it wasn't added again. */
    data class Duplicate(override val title: String, override val authors: List<String>) : UploadResult
}
