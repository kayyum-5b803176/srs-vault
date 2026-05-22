package com.srspassword.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srspassword.app.ui.screens.*

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
}

@Composable
fun AppNavHost(
    startDestination: String = Screen.Dashboard.route,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartReview    = { navController.navigate(Screen.Review.route) },
                onViewAllCards   = { navController.navigate(Screen.CardList.route) },
                onAddCard        = { navController.navigate(Screen.AddCard.route) },
                onOpenSettings   = { navController.navigate(Screen.Settings.route) },
                onOpenStats      = { navController.navigate(Screen.Stats.route) }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(
                onFinished = { navController.popBackStack() }
            )
        }

        composable(Screen.CardList.route) {
            CardListScreen(
                onAddCard      = { navController.navigate(Screen.AddCard.route) },
                onCardClick    = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) },
                onBack         = { navController.popBackStack() }
            )
        }

        composable(Screen.AddCard.route) {
            AddEditCardScreen(
                cardId   = null,
                onSaved  = { navController.popBackStack() },
                onBack   = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditCard.route,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStackEntry ->
            AddEditCardScreen(
                cardId  = backStackEntry.arguments?.getString("cardId"),
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CardDetail.route,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStackEntry ->
            CardDetailScreen(
                cardId   = backStackEntry.arguments?.getString("cardId") ?: "",
                onEdit   = { id -> navController.navigate(Screen.EditCard.createRoute(id)) },
                onBack   = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onImportExport = { navController.navigate(Screen.ImportExport.route) },
                onBack         = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportExport.route) {
            ImportExportScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
