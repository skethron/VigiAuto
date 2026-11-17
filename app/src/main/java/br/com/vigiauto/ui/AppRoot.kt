package br.com.vigiauto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.data.prefs.AppSettings
import br.com.vigiauto.ui.details.DetailsScreen
import br.com.vigiauto.ui.history.HistoryScreen
import br.com.vigiauto.ui.main.MainScreen
import br.com.vigiauto.ui.settings.SettingsScreen
import br.com.vigiauto.ui.theme.VigiAutoTheme

object Routes {
    const val MAIN = "main"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{id}"

    fun details(id: Long) = "details/$id"
}

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    VigiAutoTheme(mode = settings.themeMode) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                MainScreen(
                    container = container,
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDetails = { id -> navController.navigate(Routes.details(id)) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { id -> navController.navigate(Routes.details(id)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                DetailsScreen(
                    container = container,
                    recordId = id,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun StatusBarAppearance(darkIcons: Boolean? = null) {
    val view = LocalView.current
    val useDarkIcons = darkIcons ?: (MaterialTheme.colorScheme.background.luminance() > 0.5f)
    val navigationDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = navigationDarkIcons
            }
        }
    }
}
