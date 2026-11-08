package io.github.redon13.easyreader.ui.reader

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.redon13.easyreader.EasyReaderApp
import io.github.redon13.easyreader.calibre.CalibreBookParser
import io.github.redon13.easyreader.calibre.CalibreException
import io.github.redon13.easyreader.calibre.ReadingPosition
import io.github.redon13.easyreader.calibre.UploadResult
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.data.AppSettings
import io.github.redon13.easyreader.data.Downloads
import io.github.redon13.easyreader.data.LastBook
import io.github.redon13.easyreader.data.ReaderSettings
import io.github.redon13.easyreader.data.ReaderTheme
import io.github.redon13.easyreader.data.ReaderTypeface
import io.github.redon13.easyreader.data.SessionState
import io.github.redon13.easyreader.data.db.DeviceBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.security.MessageDigest

/** What the reader screen is doing. */
sealed interface ReaderState {
    data class Loading(val message: String, val progress: Float? = null) : ReaderState
    data class Failed(val message: String) : ReaderState
    /** The book is open and ready to show. */
    data object Ready : ReaderState
    /** Not EPUB or PDF: hand the downloaded file to another app. */
    data class OpenElsewhere(val file: File, val format: String) : ReaderState
}

/** Where the owner is, in "pages". EPUBs have no fixed pages, so these are Readium's estimate (about 1,000 letters each). */
data class PageInfo(val page: Int, val total: Int, val leftInChapter: Int)

/** One place where the searched words were found. */
data class SearchHit(val locator: Locator, val before: String, val match: String, val after: String, val chapter: String?)

/** One entry in the table of contents, with how deeply it's nested. */
data class TocEntry(val link: Link, val title: String, val depth: Int)

/**
 * Opens a book (downloading it first if needed), finds the latest reading
 * position, and saves the place as the owner reads. Survives screen rotation.
 */
class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as EasyReaderApp).container

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading("Opening…"))
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val settings: StateFlow<ReaderSettings> = container.settings.readerSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    private val appSettings: StateFlow<AppSettings> = container.settings.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    /** Short messages for the owner (e.g. "Added to Calibre"). */
    val messages: SharedFlow<String> = _messages

    private val _addingToCalibre = MutableStateFlow(false)
    val addingToCalibre: StateFlow<Boolean> = _addingToCalibre.asStateFlow()

    /** True for books opened from the device, which can be added to Calibre. */
    val canAddToCalibre: Boolean get() = deviceBookHash != null

    private var deviceBookFile: File? = null

    private val _barsVisible = MutableStateFlow(true)
    val barsVisible: StateFlow<Boolean> = _barsVisible.asStateFlow()

    /** Heights (in pixels) of Android's status bar and navigation bar, so the reader's bars sit clear of them. */
    private val _systemBars = MutableStateFlow(0 to 0)
    val systemBars: StateFlow<Pair<Int, Int>> = _systemBars.asStateFlow()

    fun setSystemBars(top: Int, bottom: Int) {
        _systemBars.value = top to bottom
    }

    private val _locator = MutableStateFlow<Locator?>(null)
    /** Where the owner is now, for the progress bar. */
    val locator: StateFlow<Locator?> = _locator.asStateFlow()

    var publication: Publication? = null
        private set
    var format: String = "EPUB"
        private set
    var title: String = ""
        private set
    var toc: List<TocEntry> = emptyList()
        private set
    /** Every "page" in the book, in order. */
    var pages: List<Locator> = emptyList()
        private set
    val isPdf get() = format == "PDF"

    /** Where to start: the saved position at first, then wherever the owner got to. */
    private var startLocator: Locator? = null

    // Which book this is, for saving the place.
    private var libraryId: String? = null
    private var bookId: Int? = null
    private var deviceBookHash: String? = null

    /** Whether the book is already marked finished in Calibre (so it's only marked once). */
    private var finished = true
    private var started = false
    private var lastSentAt = 0L

    fun start(intent: Intent) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val data = intent.data
                if (intent.action == Intent.ACTION_VIEW && data != null) openDeviceFile(data)
                else openCalibreBook(intent.getStringExtra(EXTRA_LIBRARY) ?: "", intent.getIntExtra(EXTRA_BOOK, -1))
            } catch (e: CalibreException) {
                _state.value = ReaderState.Failed(
                    if (e is CalibreException.CannotReach) "This book isn't on the device yet, and the server can't be reached. Check Tailscale and try again."
                    else e.message ?: "Something went wrong."
                )
            } catch (e: Exception) {
                _state.value = ReaderState.Failed("This book couldn't be opened. (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    private suspend fun openCalibreBook(library: String, id: Int) {
        val signedIn = container.sessions.state.first { it !is SessionState.Loading } as? SessionState.SignedIn
        val session = signedIn?.session ?: return fail("Please sign in to EasyReader first.")
        val entity = container.database.books().book(library, id) ?: return fail("This book is no longer in the library.")
        val book = CalibreBookParser.parse(entity.id, entity.rawJson)
        val chosen = Downloads.bestFormat(book) ?: return fail("This book has no files in Calibre.")
        libraryId = library
        bookId = id
        title = book.title
        container.settings.saveLastBook(LastBook(library, id))
        finished = book.isFinished

        _state.value = ReaderState.Loading("Downloading…", 0f)
        val file = container.downloads.fileFor(session, book, chosen) { p ->
            _state.value = ReaderState.Loading("Downloading…", p)
        }
        if (chosen !in Downloads.READABLE) {
            _state.value = ReaderState.OpenElsewhere(file, chosen)
            return
        }

        _state.value = ReaderState.Loading("Opening…")
        val position = container.positions.latest(session, id)
        format = chosen
        startLocator = position?.takeIf { it.format == null || it.format == chosen }?.locator?.let(::parseLocator)
        open(file)
    }

    private suspend fun openDeviceFile(uri: Uri) {
        _state.value = ReaderState.Loading("Opening…")
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        format = if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) "PDF" else "EPUB"

        // Keep a copy: Android only lets us read the original for a short while.
        val (file, hash) = withContext(Dispatchers.IO) {
            val dir = File(getApplication<Application>().filesDir, "device-books").apply { mkdirs() }
            val temp = File(dir, "incoming.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            (resolver.openInputStream(uri) ?: error("The file couldn't be read")).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(32)
            val dest = File(dir, "$hash.${format.lowercase()}")
            if (dest.exists()) temp.delete() else temp.renameTo(dest)
            dest to hash
        }
        deviceBookHash = hash
        deviceBookFile = file
        val dao = container.database.reading()
        val existing = dao.deviceBook(hash)
        title = existing?.title ?: name.substringBeforeLast('.')
        startLocator = ReadingPosition.parse(existing?.positionJson)?.locator?.let(::parseLocator)
        open(file)
        publication?.metadata?.title?.takeIf { it.isNotBlank() }?.let { title = it }
        dao.saveDeviceBook(DeviceBookEntity(hash, title, file.path, format, existing?.positionJson, System.currentTimeMillis()))
    }

    private suspend fun open(file: File) {
        val context = getApplication<Application>()
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(
            DefaultPublicationParser(context, httpClient, assetRetriever, PdfiumDocumentFactory(context))
        )
        val asset = assetRetriever.retrieve(file).getOrElse { return fail("This file couldn't be read.") }
        val pub = opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            return fail("This file couldn't be opened. It may be damaged or in a format EasyReader doesn't support.")
        }
        publication = pub
        toc = flatten(pub.tableOfContents, 0)
        pages = pub.positions()
        if (title.isBlank()) title = pub.metadata.title.orEmpty()
        _state.value = ReaderState.Ready
    }

    /** Page number, total pages and pages left in the chapter for a place in the book. */
    fun pageInfo(locator: Locator?): PageInfo? {
        if (pages.isEmpty() || locator == null) return null
        val index = (locator.locations.position?.minus(1)
            ?: ((locator.locations.totalProgression ?: 0.0) * pages.size).toInt())
            .coerceIn(0, pages.lastIndex)
        val href = pages[index].href
        var left = 0
        for (i in index + 1 until pages.size) {
            if (pages[i].href != href) break
            left++
        }
        return PageInfo(index + 1, pages.size, left)
    }

    private fun flatten(links: List<Link>, depth: Int): List<TocEntry> = links.flatMap { link ->
        listOf(TocEntry(link, link.title ?: "Untitled", depth)) + flatten(link.children, depth + 1)
    }

    private fun fail(message: String) {
        _state.value = ReaderState.Failed(message)
    }

    private fun parseLocator(json: String): Locator? =
        runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()

    // ---- Showing the book ----------------------------------------------------

    /** Creates the Readium page view. Called again after rotation, starting from the current place. */
    @OptIn(ExperimentalReadiumApi::class)
    fun fragmentFactory(): FragmentFactory? {
        val pub = publication ?: return null
        val start = _locator.value ?: startLocator
        return if (isPdf) {
            PdfNavigatorFactory(pub, PdfiumEngineProvider())
                .createFragmentFactory(initialLocator = start, initialPreferences = pdfPreferences())
        } else {
            EpubNavigatorFactory(pub).createFragmentFactory(
                initialLocator = start,
                initialPreferences = epubPreferences(settings.value),
                configuration = EpubNavigatorFragment.Configuration {
                    // Bundled fonts the reader can use.
                    servedAssets += "fonts/.*"
                    addFontFamilyDeclaration(FontFamily(ALEGREYA)) {
                        addFontFace {
                            addSource("fonts/alegreya.ttf")
                            setFontWeight(400..900)
                        }
                    }
                    addFontFamilyDeclaration(FontFamily(ALEGREYA_SANS)) {
                        addFontFace {
                            addSource("fonts/alegreya_sans_regular.ttf")
                            setFontWeight(FontWeight.NORMAL)
                        }
                        addFontFace {
                            addSource("fonts/alegreya_sans_bold.ttf")
                            setFontWeight(FontWeight.BOLD)
                        }
                    }
                },
            )
        }
    }

    fun pdfPreferences() = PdfiumPreferences(scrollAxis = Axis.HORIZONTAL)

    fun epubPreferences(s: ReaderSettings): EpubPreferences {
        val original = s.typeface == ReaderTypeface.ORIGINAL
        return EpubPreferences(
            theme = when (s.theme) {
                ReaderTheme.WHITE -> Theme.LIGHT
                ReaderTheme.CAPPUCCINO -> Theme.SEPIA
                ReaderTheme.DARK -> Theme.DARK
            },
            backgroundColor = Color(s.theme.background.toInt()),
            textColor = Color(s.theme.text.toInt()),
            fontSize = s.fontSize,
            fontFamily = when (s.typeface) {
                ReaderTypeface.ALEGREYA -> FontFamily(ALEGREYA)
                ReaderTypeface.ALEGREYA_SANS -> FontFamily(ALEGREYA_SANS)
                ReaderTypeface.ORIGINAL -> null
            },
            // The book's own styles are kept only with its own font.
            publisherStyles = original,
            lineHeight = if (original) null else 1.55,
            scroll = false,
        )
    }

    fun updateSettings(change: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { container.settings.saveReaderSettings(change(settings.value)) }
    }

    /** Sends a book opened from the device to Calibre, so it syncs like the others. */
    fun addToCalibre() {
        val file = deviceBookFile ?: return
        if (_addingToCalibre.value) return
        _addingToCalibre.value = true
        viewModelScope.launch {
            try {
                val session = (container.sessions.state.value as? SessionState.SignedIn)?.session
                if (session == null) {
                    _messages.tryEmit("Please sign in to EasyReader first.")
                    return@launch
                }
                val name = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Book" } + "." + format.lowercase()
                val result = session.client.uploadBook(session.libraryId, name, file.length(), { file.inputStream() })
                _messages.tryEmit(
                    when (result) {
                        is UploadResult.Added -> "Added to Calibre."
                        is UploadResult.Duplicate -> "Calibre already has this book."
                    }
                )
                if (result is UploadResult.Added) container.library.sync(session)
            } catch (e: CalibreException) {
                _messages.tryEmit("Couldn't add it: ${e.message}")
            } finally {
                _addingToCalibre.value = false
            }
        }
    }

    // ---- Search inside the book (EPUB only) ----------------------------------

    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    @OptIn(ExperimentalReadiumApi::class)
    val canSearch: Boolean get() = publication?.isSearchable == true

    /** Finds every place the words appear (up to [MAX_HITS]), adding results as they're found. */
    @OptIn(ExperimentalReadiumApi::class)
    fun search(query: String) {
        searchJob?.cancel()
        _searchHits.value = emptyList()
        val pub = publication
        if (query.isBlank() || pub == null) {
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            val iterator = pub.search(query.trim())
            try {
                while (iterator != null && _searchHits.value.size < MAX_HITS) {
                    val page = iterator.next().getOrNull() ?: break
                    _searchHits.value = _searchHits.value + page.locators.map { l ->
                        SearchHit(
                            locator = l,
                            before = l.text.before.orEmpty().takeLast(60).trimStart(),
                            match = l.text.highlight.orEmpty(),
                            after = l.text.after.orEmpty().take(60).trimEnd(),
                            chapter = l.title,
                        )
                    }
                }
            } finally {
                iterator?.close()
                _searching.value = false
            }
        }
    }

    fun toggleBars() {
        _barsVisible.value = !_barsVisible.value
    }

    fun hideBars() {
        _barsVisible.value = false
    }

    // ---- Saving the place --------------------------------------------------------

    /** Called whenever the page changes. Saves on the device each time, and sends to Calibre now and then. */
    fun onLocatorChanged(locator: Locator) {
        _locator.value = locator
        // Reaching 95% marks the book finished (once; "Mark as unread" undoes it).
        val lib = libraryId
        val id = bookId
        if (!finished && lib != null && id != null && (locator.locations.totalProgression ?: 0.0) >= appSettings.value.finishedAt) {
            finished = true
            container.appScope.launch { container.changes.setFinished(lib, id, true) }
        }
        val now = System.currentTimeMillis()
        val send = now - lastSentAt > SEND_EVERY_MS
        if (send) lastSentAt = now
        save(locator, send)
    }

    /** Called when leaving the book or the app: saves and sends straight away. */
    fun onLeave() {
        _locator.value?.let { save(it, send = true) }
        lastSentAt = System.currentTimeMillis()
    }

    private fun save(locator: Locator, send: Boolean) {
        val position = ReadingPosition(
            locator = locator.toJSON().toString(),
            progress = locator.locations.totalProgression ?: 0.0,
            timestamp = System.currentTimeMillis(),
            device = container.positions.deviceName,
            format = format,
        )
        // Saved on the app's own scope so it isn't lost when the screen closes.
        container.appScope.launch {
            val lib = libraryId
            val id = bookId
            val hash = deviceBookHash
            if (lib != null && id != null) {
                container.positions.save(lib, id, position)
                if (send) container.positions.send()
            } else if (hash != null) {
                container.database.reading().saveDeviceBookPosition(hash, position.toJson())
            }
        }
    }

    override fun onCleared() {
        publication?.close()
        super.onCleared()
    }

    companion object {
        const val EXTRA_LIBRARY = "library"
        const val EXTRA_BOOK = "book"
        const val ALEGREYA = "Alegreya"
        const val ALEGREYA_SANS = "Alegreya Sans"

        /** While reading, Calibre is updated at most this often (and always on leaving). */
        private const val SEND_EVERY_MS = 30_000L
        private const val MAX_HITS = 500

    }
}
