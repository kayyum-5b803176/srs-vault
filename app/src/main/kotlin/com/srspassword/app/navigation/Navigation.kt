package com.srspassword.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srspassword.app.ui.screens.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object Dashboard   : Screen("dashboard")
    object Review      : Screen("review")
    object CardList    : Screen("card_list")
    object AddCard     : Screen("add_card")
    object EditCard    : Screen("edit_card/{cardId}") {
        fun createRoute(cardId: String) = "edit_card/$cardId"
    }
    object CardDetail  : Screen("card_detail/{cardId}") {
        fun createRoute(cardId: String) = "card_detail/$cardId"
    }
    object Settings    : Screen("settings")
    object ImportExport: Screen("import_export")
    object Stats       : Screen("stats")
    object PinSetup    : Screen("pin_setup")
}

@Composable
fun AppNavHost(
    startDestination: String             = Screen.Dashboard.route,
    navController   : NavHostController  = rememberNavController()
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartReview  = { navController.navigate(Screen.Review.route) },
                onViewAllCards = { navController.navigate(Screen.CardList.route) },
                onAddCard      = { navController.navigate(Screen.AddCard.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenStats    = { navController.navigate(Screen.Stats.route) }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(onFinished = { navController.popBackStack() })
        }

        composable(Screen.CardList.route) {
            CardListScreen(
                onAddCard   = { navController.navigate(Screen.AddCard.route) },
                onCardClick = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) },
                onBack      = { navController.popBackStack() }
            )
        }

        composable(Screen.AddCard.route) {
            AddEditCardScreen(
                cardId  = null,
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.EditCard.route,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { back ->
            AddEditCardScreen(
                cardId  = back.arguments?.getString("cardId"),
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.CardDetail.route,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { back ->
            CardDetailScreen(
                cardId = back.arguments?.getString("cardId") ?: "",
                onEdit = { id -> navController.navigate(Screen.EditCard.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onImportExport = { navController.navigate(Screen.ImportExport.route) },
                onSetupPin     = { navController.navigate(Screen.PinSetup.route) },
                onBack         = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportExport.route) {
            ImportExportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        // ── Master PIN setup / change ─────────────────────────────────────────
        composable(Screen.PinSetup.route) {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val isPinSet = settingsVm.isPinSet.value  // read current value for copy

            PinSetupScreen(
                isChangingPin  = isPinSet,
                onPinConfirmed = { pin ->
                    settingsVm.savePin(pin, pin.length)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
