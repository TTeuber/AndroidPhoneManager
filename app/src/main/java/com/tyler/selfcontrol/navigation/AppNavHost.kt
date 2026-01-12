package com.tyler.selfcontrol.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tyler.selfcontrol.ui.screens.BlockEditScreen
import com.tyler.selfcontrol.ui.screens.MainScreen
import com.tyler.selfcontrol.ui.screens.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Main.route,
        modifier = modifier
    ) {
        composable(NavRoutes.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                },
                onNavigateToBlockEdit = { blockId ->
                    navController.navigate(NavRoutes.BlockEdit.createRoute(blockId))
                }
            )
        }
        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = NavRoutes.BlockEdit.route,
            arguments = listOf(
                navArgument("blockId") { type = NavType.LongType }
            )
        ) {
            BlockEditScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
