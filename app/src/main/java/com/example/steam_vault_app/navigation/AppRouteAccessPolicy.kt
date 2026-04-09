package com.example.steam_vault_app.navigation

object AppRouteAccessPolicy {
    private val preSetupAllowedRoutes = setOf(
        AppRoute.Welcome,
        AppRoute.CreatePassword,
        AppRoute.BackupRestore,
    )

    private val lockedAllowedRoutes = setOf(
        AppRoute.Unlock,
        AppRoute.BackupRestore,
    )

    fun canAccessBeforePasswordSetup(route: AppRoute): Boolean {
        return route in preSetupAllowedRoutes
    }

    fun canAccessWhileLocked(route: AppRoute): Boolean {
        return route in lockedAllowedRoutes
    }
}
