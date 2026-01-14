package com.tyler.selfcontrol.navigation

sealed class NavRoutes(val route: String) {
    data object Main : NavRoutes("main")
    data object Settings : NavRoutes("settings")
    data object BlockEdit : NavRoutes("block_edit/{blockId}") {
        fun createRoute(blockId: Long) = "block_edit/$blockId"
    }
    data object AppInstallation : NavRoutes("app_installation")
    data object AllowlistManagement : NavRoutes("allowlist_management")
}
