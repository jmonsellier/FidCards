package com.monselio.fidcards.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monselio.fidcards.ui.detail.CardDetailScreen
import com.monselio.fidcards.ui.edit.EditCardScreen
import com.monselio.fidcards.ui.home.HomeScreen
import com.monselio.fidcards.ui.navigation.Destinations
import com.monselio.fidcards.ui.scan.ScanScreen

@Composable
fun FidCardsApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                onCardClick = { id -> navController.navigate(Destinations.cardDetail(id)) },
                onAddCard = { navController.navigate(Destinations.SCAN) },
            )
        }

        composable(
            route = Destinations.CARD_DETAIL,
            arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            CardDetailScreen(
                cardId = cardId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Destinations.edit(cardId)) },
            )
        }

        composable(Destinations.SCAN) {
            ScanScreen(
                onScanned = { value, format ->
                    navController.navigate(Destinations.editScanned(value, format.name)) {
                        popUpTo(Destinations.HOME)
                    }
                },
                onManualEntry = {
                    navController.navigate(Destinations.editNew()) {
                        popUpTo(Destinations.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destinations.EDIT,
            arguments = listOf(
                navArgument("cardId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("value") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("format") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val args = entry.arguments
            val cardId = args?.getLong("cardId")?.takeIf { it >= 0 }
            EditCardScreen(
                cardId = cardId,
                scannedValue = args?.getString("value"),
                scannedFormat = args?.getString("format"),
                onDone = { navController.popBackStack() },
                onDeleted = {
                    navController.popBackStack(Destinations.HOME, inclusive = false)
                },
            )
        }
    }
}
