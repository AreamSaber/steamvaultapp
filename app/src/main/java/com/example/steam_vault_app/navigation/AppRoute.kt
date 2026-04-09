package com.example.steam_vault_app.navigation

import androidx.annotation.StringRes
import com.example.steam_vault_app.R

sealed class AppRoute(
    val route: String,
    @StringRes val titleResId: Int,
    @StringRes val shortLabelResId: Int,
    val isSensitive: Boolean = false,
) {
    data object Welcome : AppRoute(
        route = "welcome",
        titleResId = R.string.route_title_welcome,
        shortLabelResId = R.string.route_short_welcome,
    )

    data object CreatePassword : AppRoute(
        route = "create_password",
        titleResId = R.string.route_title_create_password,
        shortLabelResId = R.string.route_short_create_password,
        isSensitive = true,
    )

    data object Unlock : AppRoute(
        route = "unlock",
        titleResId = R.string.route_title_unlock,
        shortLabelResId = R.string.route_short_unlock,
        isSensitive = true,
    )

    data object ChangePassword : AppRoute(
        route = "change_password",
        titleResId = R.string.route_title_change_password,
        shortLabelResId = R.string.route_short_change_password,
        isSensitive = true,
    )

    data object Tokens : AppRoute(
        route = "tokens",
        titleResId = R.string.route_title_tokens,
        shortLabelResId = R.string.route_short_tokens,
        isSensitive = true,
    )

    data object TokenDetail : AppRoute(
        route = "token/{tokenId}",
        titleResId = R.string.route_title_token_detail,
        shortLabelResId = R.string.route_short_token_detail,
        isSensitive = true,
    ) {
        const val tokenIdArgument = "tokenId"

        fun createRoute(tokenId: String): String {
            return "token/$tokenId"
        }
    }

    data object Import : AppRoute(
        route = "import",
        titleResId = R.string.route_title_import,
        shortLabelResId = R.string.route_short_import,
        isSensitive = true,
    )

    data object SteamProtocolLogin : AppRoute(
        route = "steam_protocol_login",
        titleResId = R.string.route_title_steam_add_authenticator,
        shortLabelResId = R.string.route_short_steam_add_authenticator,
        isSensitive = true,
    )

    data object SteamAddAuthenticator : AppRoute(
        route = "steam_add_authenticator",
        titleResId = R.string.route_title_steam_add_authenticator,
        shortLabelResId = R.string.route_short_steam_add_authenticator,
        isSensitive = true,
    )

    data object SteamAuthenticatorBinding : AppRoute(
        route = "steam_authenticator_binding",
        titleResId = R.string.route_title_steam_authenticator_binding,
        shortLabelResId = R.string.route_short_steam_authenticator_binding,
        isSensitive = true,
    )

    data object Settings : AppRoute(
        route = "settings",
        titleResId = R.string.route_title_settings,
        shortLabelResId = R.string.route_short_settings,
    )

    data object BackupExport : AppRoute(
        route = "backup_export",
        titleResId = R.string.route_title_backup_export,
        shortLabelResId = R.string.route_short_backup_export,
        isSensitive = true,
    )

    data object BackupRestore : AppRoute(
        route = "backup_restore",
        titleResId = R.string.route_title_backup_restore,
        shortLabelResId = R.string.route_short_backup_restore,
        isSensitive = true,
    )

    data object CloudBackupStatus : AppRoute(
        route = "cloud_backup_status",
        titleResId = R.string.route_title_cloud_backup_status,
        shortLabelResId = R.string.route_short_cloud_backup_status,
        isSensitive = true,
    )

    data object CloudBackupConfig : AppRoute(
        route = "cloud_backup_config",
        titleResId = R.string.route_title_cloud_backup_config,
        shortLabelResId = R.string.route_short_cloud_backup_config,
        isSensitive = true,
    )

    data object SteamSession : AppRoute(
        route = "steam_session/{tokenId}",
        titleResId = R.string.route_title_steam_session,
        shortLabelResId = R.string.route_short_steam_session,
        isSensitive = true,
    ) {
        const val tokenIdArgument = "tokenId"

        fun createRoute(tokenId: String): String {
            return "steam_session/$tokenId"
        }
    }

    data object SteamConfirmations : AppRoute(
        route = "steam_confirmations/{tokenId}",
        titleResId = R.string.route_title_steam_confirmations,
        shortLabelResId = R.string.route_short_steam_confirmations,
        isSensitive = true,
    ) {
        const val tokenIdArgument = "tokenId"

        fun createRoute(tokenId: String): String {
            return "steam_confirmations/$tokenId"
        }
    }

    data object SteamQrLogin : AppRoute(
        route = "steam_qr_login",
        titleResId = R.string.route_title_steam_qr_login,
        shortLabelResId = R.string.route_short_steam_qr_login,
        isSensitive = true,
    )

    companion object {
        val allRoutes = listOf(
            Welcome,
            CreatePassword,
            Unlock,
            ChangePassword,
            Tokens,
            TokenDetail,
            Import,
            SteamProtocolLogin,
            SteamAddAuthenticator,
            SteamAuthenticatorBinding,
            Settings,
            BackupExport,
            BackupRestore,
            CloudBackupStatus,
            CloudBackupConfig,
            SteamSession,
            SteamConfirmations,
            SteamQrLogin,
        )

        val bottomNavigationRoutes = listOf(
            Tokens,
            Import,
            Settings,
        )

        fun fromRoute(route: String?): AppRoute {
            val normalizedRoute = route?.substringBefore("?") ?: return Welcome

            return when {
                normalizedRoute.startsWith("token/") -> TokenDetail
                normalizedRoute.startsWith("steam_session/") -> SteamSession
                normalizedRoute.startsWith("steam_confirmations/") -> SteamConfirmations
                else -> allRoutes.firstOrNull { it.route == normalizedRoute } ?: Welcome
            }
        }
    }
}
