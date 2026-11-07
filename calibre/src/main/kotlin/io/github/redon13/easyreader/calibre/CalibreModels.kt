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
    val rawJson: String,
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
