package com.example.steam_vault_app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteAccessPolicyTest {
    @Test
    fun canAccessBeforePasswordSetup_allowsCreateAndRestoreRoutes() {
        assertTrue(AppRouteAccessPolicy.canAccessBeforePasswordSetup(AppRoute.Welcome))
        assertTrue(AppRouteAccessPolicy.canAccessBeforePasswordSetup(AppRoute.CreatePassword))
        assertTrue(AppRouteAccessPolicy.canAccessBeforePasswordSetup(AppRoute.BackupRestore))
        assertFalse(AppRouteAccessPolicy.canAccessBeforePasswordSetup(AppRoute.Tokens))
    }

    @Test
    fun canAccessWhileLocked_allowsUnlockAndRestoreRoutes() {
        assertTrue(AppRouteAccessPolicy.canAccessWhileLocked(AppRoute.Unlock))
        assertTrue(AppRouteAccessPolicy.canAccessWhileLocked(AppRoute.BackupRestore))
        assertFalse(AppRouteAccessPolicy.canAccessWhileLocked(AppRoute.CreatePassword))
        assertFalse(AppRouteAccessPolicy.canAccessWhileLocked(AppRoute.Tokens))
    }
}
