package io.github.redon13.easyreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.redon13.easyreader.ui.details.BookDetailsScreen
import io.github.redon13.easyreader.ui.home.HomeScreen
import io.github.redon13.easyreader.ui.login.LoginScreen
import io.github.redon13.easyreader.ui.theme.EasyColors

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
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.background(EasyColors.Background),
    ) {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpenBook = { nav.navigate("book/$it") },
                onSeeAll = { nav.navigate("list/${it.name}") },
            )
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
    }
}
