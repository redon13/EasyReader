package io.github.redon13.easyreader.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.redon13.easyreader.R
import io.github.redon13.easyreader.data.ReaderSettings
import io.github.redon13.easyreader.ui.theme.EasyReaderTheme
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import java.io.File

/**
 * The reader. Opens Calibre books (from the details page) and EPUB/PDF files
 * from the device ("Open with" in other apps).
 */
class ReaderActivity : AppCompatActivity() {

    private val vm: ReaderViewModel by viewModels()
    private var navigator: VisualNavigator? = null
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        // After a rotation the book is still open: Android can rebuild the page
        // view itself. Otherwise start fresh and add it once the book is ready.
        val factory = vm.fragmentFactory()
        if (factory != null) {
            supportFragmentManager.fragmentFactory = factory
            super.onCreate(savedInstanceState)
        } else {
            super.onCreate(null)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        root = FrameLayout(this)
        val pages = FragmentContainerView(this).apply { id = R.id.reader_pages }
        val topBar = ComposeView(this).apply {
            setContent { EasyReaderTheme { ReaderTopOverlay(vm, onBack = ::finish) } }
        }
        val bottomBar = ComposeView(this).apply {
            setContent { EasyReaderTheme { ReaderBottomBar(vm, onGo = ::goTo, onGoToPage = ::goToPage) } }
        }
        val status = ComposeView(this).apply {
            setContent { EasyReaderTheme { ReaderStatusScreen(vm, onClose = ::finish) } }
        }
        // Leave a strip at the bottom for the "pages left in chapter" footer.
        root.addView(
            pages,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                bottomMargin = (FOOTER_HEIGHT_DP * resources.displayMetrics.density).toInt()
            },
        )
        root.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        root.addView(bottomBar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(status, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        // Remember how tall Android's own bars are, even while they're hidden.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            vm.setSystemBars(bars.top, bars.bottom)
            insets
        }

        vm.start(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { state ->
                        status.isVisible = state !is ReaderState.Ready
                        when (state) {
                            ReaderState.Ready -> showBook()
                            is ReaderState.OpenElsewhere -> openElsewhere(state.file, state.format)
                            else -> Unit
                        }
                    }
                }
                launch { vm.settings.collect(::applySettings) }
                launch { vm.barsVisible.collect(::showSystemBars) }
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun showBook() {
        if (navigator != null) return
        if (supportFragmentManager.findFragmentByTag(PAGES_TAG) == null) {
            supportFragmentManager.fragmentFactory = vm.fragmentFactory() ?: return
            val type = if (vm.isPdf) PdfiumNavigatorFragment::class.java else EpubNavigatorFragment::class.java
            supportFragmentManager.commitNow { add(R.id.reader_pages, type, Bundle(), PAGES_TAG) }
        }
        val nav = supportFragmentManager.findFragmentByTag(PAGES_TAG) as? VisualNavigator ?: return
        navigator = nav

        // Tap the left or right edge to turn the page; tap the middle to show or hide the bars.
        (nav as? OverflowableNavigator)?.let { nav.addInputListener(DirectionalNavigationAdapter(it, animatedTransition = true)) }
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                vm.toggleBars()
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nav.currentLocator.collect { vm.onLocatorChanged(it) }
            }
        }
        applySettings(vm.settings.value)
        // Start with the bars hidden after a moment, like a printed page.
        root.postDelayed({ vm.hideBars() }, 1500)
    }

    private fun applySettings(settings: ReaderSettings) {
        root.setBackgroundColor(settings.theme.background.toInt())
        window.decorView.setBackgroundColor(settings.theme.background.toInt())
        val dark = settings.theme == io.github.redon13.easyreader.data.ReaderTheme.DARK
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        (navigator as? EpubNavigatorFragment)?.submitPreferences(vm.epubPreferences(settings))
    }

    private fun showSystemBars(show: Boolean) {
        val controller = WindowCompat.getInsetsController(window, root)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show || navigator == null) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun goTo(link: Link) {
        navigator?.go(link, animated = false)
    }

    private fun goToPage(locator: Locator) {
        navigator?.go(locator, animated = false)
    }

    /** For formats EasyReader can't show (e.g. .doc): hand the file to another app. */
    private fun openElsewhere(file: File, format: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format.lowercase()) ?: "application/octet-stream"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(view, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app on this device can open $format files.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onStop() {
        vm.onLeave()
        super.onStop()
    }

    companion object {
        private const val PAGES_TAG = "pages"
        const val FOOTER_HEIGHT_DP = 28

        /** Opens a Calibre book from the library. */
        fun open(context: Context, libraryId: String, bookId: Int) {
            context.startActivity(
                Intent(context, ReaderActivity::class.java)
                    .putExtra(ReaderViewModel.EXTRA_LIBRARY, libraryId)
                    .putExtra(ReaderViewModel.EXTRA_BOOK, bookId)
            )
        }
    }
}
