package io.github.redon13.easyreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.redon13.easyreader.AppContainer
import io.github.redon13.easyreader.data.Session
import io.github.redon13.easyreader.data.SessionState
import io.github.redon13.easyreader.ui.browse.BookListScreen
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.browse.ListKind
import io.github.redon13.easyreader.ui.browse.LocalSelectedBookId
import io.github.redon13.easyreader.ui.browse.LocalShowCoverProgress
import io.github.redon13.easyreader.ui.details.BookDetailsScreen
import io.github.redon13.easyreader.ui.home.HomeScreen
import io.github.redon13.easyreader.ui.home.TabletLibrary
import io.github.redon13.easyreader.ui.reader.ReaderActivity
import io.github.redon13.easyreader.ui.settings.SettingsScreen
import io.github.redon13.easyreader.ui.upload.UploadScreen
import io.github.redon13.easyreader.ui.login.LoginScreen
import io.github.redon13.easyreader.ui.theme.EasyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Shows the sign-in screen or the library, depending on whether the owner is signed in. */
@Composable
fun AppRoot(container: AppContainer) {
    val state by container.sessions.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(EasyColors.Background))
        is SessionState.SignedOut -> LoginScreen(container, s)
        // A fresh set of screens for each sign-in and library.
        is SessionState.SignedIn -> key(s.session.libraryId, System.identityHashCode(s.session.client)) {
            LibraryNavigation(container, s.session)
        }
    }
}

/** Moving between the home screen, "See all" lists and book details. */
@Composable
private fun LibraryNavigation(container: AppContainer, session: Session) {
    val vm = viewModel(key = "browse-${session.libraryId}-${System.identityHashCode(session.client)}") {
        BrowseViewModel(container, session)
    }
    // Each time the app comes back on screen (from another app, or the screen turning on),
    // refresh straight away if the last sync was over a minute ago, so places read on the
    // other device show up. Then, while it stays on screen, every 10 minutes.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.syncIfDue(SYNC_ON_RETURN_MS)
            while (true) {
                delay(60_000)
                vm.syncIfDue(SYNC_EVERY_MS)
            }
        }
    }
    // "Resume last book on launch": once per start of the app, not after rotation.
    val context = LocalContext.current
    var resumeChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (resumeChecked) return@LaunchedEffect
        resumeChecked = true
        if (container.settings.appSettings.first().resumeLastBook) {
            container.settings.lastBook()?.takeIf { it.libraryId == session.libraryId }?.let {
                ReaderActivity.open(context, it.libraryId, it.bookId)
            }
        }
    }

    // Tablet in landscape: books and details side by side.
    val config = LocalConfiguration.current
    val twoPane = config.screenWidthDp >= 840 && config.smallestScreenWidthDp >= 600
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val appSettings by vm.appSettings.collectAsStateWithLifecycle()

    val nav = rememberNavController()
    CompositionLocalProvider(
        LocalShowCoverProgress provides appSettings.progressOnCovers,
        LocalSelectedBookId provides if (twoPane) selectedId else null,
    ) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.background(EasyColors.Background),
        ) {
            composable("home") {
                val openSettings = { nav.navigate("settings") }
                val openUpload = { nav.navigate("upload") }
                if (twoPane) {
                    TabletLibrary(
                        vm = vm,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        onOpenSettings = openSettings,
                        onUpload = openUpload,
                    ) { id ->
                        BookDetailsScreen(
                            vm = vm,
                            bookId = id,
                            onBack = { selectedId = null },
                            onShowOnly = { category, value -> vm.showOnly(category, value) },
                        )
                    }
                } else {
                    HomeScreen(
                        vm = vm,
                        onOpenBook = { nav.navigate("book/$it") },
                        onSeeAll = { nav.navigate("list/${it.name}") },
                        onOpenSettings = openSettings,
                        onUpload = openUpload,
                    )
                }
            }
            composable("list/{kind}") { entry ->
                val kind = entry.arguments?.getString("kind")?.let { k -> ListKind.entries.firstOrNull { it.name == k } } ?: ListKind.ALL
                BookListScreen(
                    vm = vm,
                    kind = kind,
                    onBack = { nav.popBackStack() },
                    onOpenBook = { nav.navigate("book/$it") },
                )
            }
            composable("book/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                BookDetailsScreen(
                    vm = vm,
                    bookId = entry.arguments?.getInt("id") ?: -1,
                    onBack = { nav.popBackStack() },
                    onShowOnly = { category, value ->
                        // Show the matching books on the home screen.
                        vm.showOnly(category, value)
                        nav.popBackStack("home", inclusive = false)
                    },
                )
            }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("upload") { UploadScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private const val SYNC_EVERY_MS = 10 * 60 * 1000L
private const val SYNC_ON_RETURN_MS = 60 * 1000L
