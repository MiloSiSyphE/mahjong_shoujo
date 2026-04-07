package dev.mahjong.shoujo.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.mahjong.shoujo.ui.context.RoundContextScreen
import dev.mahjong.shoujo.ui.correction.CorrectionScreen
import dev.mahjong.shoujo.ui.main.MainScreen
import dev.mahjong.shoujo.ui.result.ScoringResultScreen

/**
 * End-to-end navigation graph.
 *
 * Flow:
 *   Main (image input / manual entry)
 *     → Correction (review + fix CV predictions)
 *       → RoundContext (honba, winds, riichi flags, dora)
 *         → ScoringResult (yaku, han, fu, points, explanation)
 *
 * Phase 0: Main → RoundContext → ScoringResult  (manual tile entry, no CV)
 * Phase 1: Main → Correction → RoundContext → ScoringResult
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Main.route) {

        composable(Screen.Main.route) {
            MainScreen(
                onStartManualEntry = { navController.navigate(Screen.RoundContext.route) },
                onImageRecognized  = { navController.navigate(Screen.Correction.route) },
            )
        }

        composable(Screen.Correction.route) {
            CorrectionScreen(
                onConfirmed = { navController.navigate(Screen.RoundContext.route) },
                onBack      = { navController.popBackStack() },
            )
        }

        composable(Screen.RoundContext.route) {
            RoundContextScreen(
                onScore = { navController.navigate(Screen.Result.route) },
                onBack  = { navController.popBackStack() },
            )
        }

        composable(Screen.Result.route) {
            ScoringResultScreen(
                onNewHand = { navController.popBackStack(Screen.Main.route, inclusive = false) },
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Main        : Screen("main")
    object Correction  : Screen("correction")
    object RoundContext: Screen("round_context")
    object Result      : Screen("result")
}
