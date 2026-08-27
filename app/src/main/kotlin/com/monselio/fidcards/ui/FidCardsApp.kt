package com.monselio.fidcards.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monselio.fidcards.ui.detail.CardDetailScreen
import com.monselio.fidcards.ui.home.HomeScreen
import com.monselio.fidcards.ui.navigation.Destinations

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
                onAddCard = { navController.navigate(Destinations.editNew()) },
            )
        }

        composable(
            route = Destinations.CARD_DETAIL,
            arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            CardDetailScreen(cardId = cardId, onBack = { navController.popBackStack() })
        }

        composable(Destinations.EDIT) {
            // Remplacé par l'écran d'ajout / édition en phase 4.
            PlaceholderScreen("Ajout / édition — à venir")
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
