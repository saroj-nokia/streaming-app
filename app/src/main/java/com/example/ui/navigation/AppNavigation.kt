package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.catalog.CatalogScreen
import com.example.ui.catalog.CatalogViewModel
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel

object Destinations {
    const val CATALOG = "catalog"
    const val PLAYER = "player/{videoId}"
    
    fun playerRoute(videoId: Long): String = "player/$videoId"
}

@Composable
fun AppNavigation(
    catalogViewModel: CatalogViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.CATALOG
    ) {
        composable(Destinations.CATALOG) {
            CatalogScreen(
                viewModel = catalogViewModel,
                onPlayVideo = { videoId ->
                    navController.navigate(Destinations.playerRoute(videoId))
                }
            )
        }

        composable(
            route = Destinations.PLAYER,
            arguments = listOf(navArgument("videoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getLong("videoId") ?: 0L
            PlayerScreen(
                videoId = videoId,
                viewModel = playerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
