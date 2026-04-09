package com.example.steam_vault_app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingProgressRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingApiClient
import com.example.steam_vault_app.data.importing.SteamImportParser
import com.example.steam_vault_app.domain.sync.SteamConfirmationSyncManager
import com.example.steam_vault_app.domain.sync.SteamQrLoginApprovalManager
import com.example.steam_vault_app.domain.sync.SteamSessionValidationSyncManager
import com.example.steam_vault_app.feature.backup.BackupExportScreen
import com.example.steam_vault_app.feature.backup.BackupRestoreScreen
import com.example.steam_vault_app.feature.cloudbackup.CloudBackupConfigScreen
import com.example.steam_vault_app.feature.cloudbackup.CloudBackupStatusScreen
import com.example.steam_vault_app.feature.importtoken.ImportTokenEntryContext
import com.example.steam_vault_app.feature.importtoken.ImportTokenScreen
import com.example.steam_vault_app.feature.importtoken.ImportTokenEntryContextStore
import com.example.steam_vault_app.feature.importtoken.SteamAddAuthenticatorScreen
import com.example.steam_vault_app.feature.importtoken.SteamProtocolLoginScreen
import com.example.steam_vault_app.feature.importtoken.SteamAuthenticatorBindingScreen
import com.example.steam_vault_app.feature.password.ChangePasswordScreen
import com.example.steam_vault_app.feature.password.CreatePasswordScreen
import com.example.steam_vault_app.feature.settings.SettingsScreen
import com.example.steam_vault_app.feature.steamconfirmations.SteamConfirmationsScreen
import com.example.steam_vault_app.feature.steamqrlogin.SteamQrLoginScreen
import com.example.steam_vault_app.feature.steamsession.SteamSessionEntryContextStore
import com.example.steam_vault_app.feature.steamsession.SteamSessionScreen
import com.example.steam_vault_app.feature.tokens.TokenDetailScreen
import com.example.steam_vault_app.feature.tokens.TokenListScreen
import com.example.steam_vault_app.feature.unlock.UnlockScreen
import com.example.steam_vault_app.feature.welcome.WelcomeScreen

@Composable
fun SteamVaultNavHost(
    navController: NavHostController,
    startDestination: String,
    vaultRepository: VaultRepository,
    cloudBackupRepository: CloudBackupRepository,
    steamAuthenticatorBindingContextRepository: SteamAuthenticatorBindingContextRepository,
    steamAuthenticatorBindingProgressRepository: SteamAuthenticatorBindingProgressRepository,
    steamAuthenticatorEnrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    steamSessionRepository: SteamSessionRepository,
    importParser: SteamImportParser,
    cloudBackupSyncManager: CloudBackupSyncManager,
    steamAuthenticatorBindingApiClient: SteamAuthenticatorBindingApiClient,
    steamConfirmationSyncManager: SteamConfirmationSyncManager,
    steamQrLoginApprovalManager: SteamQrLoginApprovalManager,
    steamSessionValidationSyncManager: SteamSessionValidationSyncManager,
    vaultCryptography: VaultCryptography,
    securitySettings: AppSecuritySettings,
    steamTimeSyncState: SteamTimeSyncState,
    isSyncingSteamTime: Boolean,
    vaultRefreshVersion: Int,
    onCreatePassword: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    onLockVault: () -> Unit,
    onBackupRestored: () -> Unit,
    onCloudBackupRestored: () -> Unit,
    onAuthenticatorBound: () -> Unit,
    onSecuritySettingsChanged: (AppSecuritySettings) -> Unit,
    onBiometricQuickUnlockToggle: (Boolean) -> Unit,
    onSyncSteamTime: () -> Unit,
    biometricQuickUnlockAvailable: Boolean,
    biometricStatusMessage: String?,
    securityStatusMessage: String?,
    showBiometricUnlockOnUnlockScreen: Boolean,
    onImportToken: (String, String, String, ImportTokenEntryContext?) -> Unit,
    isSubmittingPasswordAction: Boolean,
    isSubmittingImport: Boolean,
    passwordCreationError: String?,
    passwordChangeError: String?,
    unlockError: String?,
    importError: String?,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoute.Welcome.route) {
            WelcomeScreen(
                onCreatePassword = { navController.navigate(AppRoute.CreatePassword.route) },
                onRestoreBackup = { navController.navigate(AppRoute.BackupRestore.route) },
            )
        }
        composable(AppRoute.CreatePassword.route) {
            CreatePasswordScreen(
                onPasswordCreated = onCreatePassword,
                isSubmitting = isSubmittingPasswordAction,
                errorMessage = passwordCreationError,
            )
        }
        composable(AppRoute.ChangePassword.route) {
            ChangePasswordScreen(
                onPasswordChanged = onChangePassword,
                isSubmitting = isSubmittingPasswordAction,
                errorMessage = passwordChangeError,
            )
        }
        composable(AppRoute.Unlock.route) {
            UnlockScreen(
                onUnlock = onUnlock,
                onUnlockWithBiometric = onUnlockWithBiometric,
                onRestoreBackup = { navController.navigate(AppRoute.BackupRestore.route) },
                showBiometricUnlock = showBiometricUnlockOnUnlockScreen,
                isSubmitting = isSubmittingPasswordAction,
                errorMessage = unlockError,
            )
        }
        composable(AppRoute.Tokens.route) {
            TokenListScreen(
                vaultRepository = vaultRepository,
                vaultCryptography = vaultCryptography,
                steamTimeOffsetSeconds = steamTimeSyncState.offsetSeconds,
                refreshVersion = vaultRefreshVersion,
                onAddAccount = { navController.navigate(AppRoute.Import.route) },
                onOpenSteamQrLogin = { navController.navigate(AppRoute.SteamQrLogin.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenTokenDetails = { tokenId ->
                    navController.navigate(AppRoute.TokenDetail.createRoute(tokenId))
                },
            )
        }
        composable(
            route = AppRoute.TokenDetail.route,
            arguments = listOf(
                navArgument(AppRoute.TokenDetail.tokenIdArgument) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val tokenId = backStackEntry.arguments
                ?.getString(AppRoute.TokenDetail.tokenIdArgument)
                .orEmpty()
            TokenDetailScreen(
                tokenId = tokenId,
                vaultRepository = vaultRepository,
                steamSessionRepository = steamSessionRepository,
                vaultCryptography = vaultCryptography,
                steamTimeOffsetSeconds = steamTimeSyncState.offsetSeconds,
                refreshVersion = vaultRefreshVersion,
                onOpenSteamSession = {
                    navController.navigate(AppRoute.SteamSession.createRoute(tokenId))
                },
                onOpenSteamConfirmations = {
                    navController.navigate(AppRoute.SteamConfirmations.createRoute(tokenId))
                },
            )
        }
        composable(AppRoute.Import.route) {
            val importEntryContext = remember {
                ImportTokenEntryContextStore.consume()
            }
            ImportTokenScreen(
                onOpenSteamAddAuthenticator = {
                    navController.navigate(AppRoute.SteamProtocolLogin.route)
                },
                onOpenSteamBrowserLogin = {
                    navController.navigate(AppRoute.SteamAddAuthenticator.route)
                },
                onSaveImport = { rawPayload, accountName, sharedSecret ->
                    onImportToken(
                        rawPayload,
                        accountName,
                        sharedSecret,
                        importEntryContext,
                    )
                },
                isSubmitting = isSubmittingImport,
                errorMessage = importError,
                entryContext = importEntryContext,
            )
        }
        composable(AppRoute.SteamProtocolLogin.route) {
            SteamProtocolLoginScreen(
                bindingContextRepository = steamAuthenticatorBindingContextRepository,
                enrollmentDraftRepository = steamAuthenticatorEnrollmentDraftRepository,
                steamProtocolLoginOrchestrator = steamProtocolLoginOrchestrator,
                onOpenBindingPreparation = {
                    navController.navigate(AppRoute.SteamAuthenticatorBinding.route)
                },
            )
        }
        composable(AppRoute.SteamAddAuthenticator.route) {
            SteamAddAuthenticatorScreen(
                bindingContextRepository = steamAuthenticatorBindingContextRepository,
                enrollmentDraftRepository = steamAuthenticatorEnrollmentDraftRepository,
                onOpenBindingPreparation = {
                    navController.navigate(AppRoute.SteamAuthenticatorBinding.route)
                },
            )
        }
        composable(AppRoute.SteamAuthenticatorBinding.route) {
            SteamAuthenticatorBindingScreen(
                bindingContextRepository = steamAuthenticatorBindingContextRepository,
                bindingProgressRepository = steamAuthenticatorBindingProgressRepository,
                enrollmentDraftRepository = steamAuthenticatorEnrollmentDraftRepository,
                steamAuthenticatorBindingApiClient = steamAuthenticatorBindingApiClient,
                steamProtocolLoginOrchestrator = steamProtocolLoginOrchestrator,
                vaultRepository = vaultRepository,
                steamSessionRepository = steamSessionRepository,
                vaultCryptography = vaultCryptography,
                onBindingCompleted = onAuthenticatorBound,
                onOpenCompatibilityImport = { entryContext ->
                    ImportTokenEntryContextStore.push(entryContext)
                    navController.navigate(AppRoute.Import.route) {
                        launchSingleTop = true
                    }
                },
                onReturnToSignIn = {
                    navController.navigate(AppRoute.SteamAddAuthenticator.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                securitySettings = securitySettings,
                onSecuritySettingsChanged = onSecuritySettingsChanged,
                onBiometricQuickUnlockToggle = onBiometricQuickUnlockToggle,
                biometricQuickUnlockAvailable = biometricQuickUnlockAvailable,
                biometricStatusMessage = biometricStatusMessage,
                securityStatusMessage = securityStatusMessage,
                onOpenChangePassword = { navController.navigate(AppRoute.ChangePassword.route) },
                onOpenBackupExport = { navController.navigate(AppRoute.BackupExport.route) },
                onOpenBackupRestore = { navController.navigate(AppRoute.BackupRestore.route) },
                onOpenCloudBackupStatus = { navController.navigate(AppRoute.CloudBackupStatus.route) },
                onOpenCloudBackupConfig = { navController.navigate(AppRoute.CloudBackupConfig.route) },
                steamTimeSyncState = steamTimeSyncState,
                isSyncingSteamTime = isSyncingSteamTime,
                onSyncSteamTime = onSyncSteamTime,
                onLockVault = onLockVault,
            )
        }
        composable(AppRoute.BackupExport.route) {
            BackupExportScreen(
                vaultRepository = vaultRepository,
            )
        }
        composable(AppRoute.BackupRestore.route) {
            BackupRestoreScreen(
                vaultRepository = vaultRepository,
                onRestoreCompleted = onBackupRestored,
            )
        }
        composable(AppRoute.CloudBackupStatus.route) {
            CloudBackupStatusScreen(
                cloudBackupRepository = cloudBackupRepository,
                cloudBackupSyncManager = cloudBackupSyncManager,
                onOpenConfiguration = { navController.navigate(AppRoute.CloudBackupConfig.route) },
                onOpenLocalBackupExport = { navController.navigate(AppRoute.BackupExport.route) },
                onOpenLocalBackupRestore = { navController.navigate(AppRoute.BackupRestore.route) },
                onCloudBackupRestored = onCloudBackupRestored,
            )
        }
        composable(AppRoute.CloudBackupConfig.route) {
            CloudBackupConfigScreen(
                cloudBackupRepository = cloudBackupRepository,
                onConfigurationSaved = {
                    navController.navigate(AppRoute.CloudBackupStatus.route) {
                        popUpTo(AppRoute.CloudBackupConfig.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = AppRoute.SteamSession.route,
            arguments = listOf(
                navArgument(AppRoute.SteamSession.tokenIdArgument) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val tokenId = backStackEntry.arguments
                ?.getString(AppRoute.SteamSession.tokenIdArgument)
                .orEmpty()
            val entryContext = remember(tokenId) {
                SteamSessionEntryContextStore.consume(tokenId)
            }
            SteamSessionScreen(
                tokenId = tokenId,
                vaultRepository = vaultRepository,
                steamSessionRepository = steamSessionRepository,
                steamProtocolLoginOrchestrator = steamProtocolLoginOrchestrator,
                steamSessionValidationSyncManager = steamSessionValidationSyncManager,
                vaultCryptography = vaultCryptography,
                importParser = importParser,
                steamTimeSyncState = steamTimeSyncState,
                onSyncSteamTime = onSyncSteamTime,
                onOpenConfirmations = {
                    navController.navigate(AppRoute.SteamConfirmations.createRoute(tokenId))
                },
                refreshVersion = vaultRefreshVersion,
                entryContext = entryContext,
            )
        }
        composable(
            route = AppRoute.SteamConfirmations.route,
            arguments = listOf(
                navArgument(AppRoute.SteamConfirmations.tokenIdArgument) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val tokenId = backStackEntry.arguments
                ?.getString(AppRoute.SteamConfirmations.tokenIdArgument)
                .orEmpty()
            SteamConfirmationsScreen(
                tokenId = tokenId,
                vaultRepository = vaultRepository,
                steamSessionRepository = steamSessionRepository,
                steamConfirmationSyncManager = steamConfirmationSyncManager,
                steamTimeSyncState = steamTimeSyncState,
                onSyncSteamTime = onSyncSteamTime,
                onOpenSteamSession = {
                    navController.navigate(AppRoute.SteamSession.createRoute(tokenId))
                },
                refreshVersion = vaultRefreshVersion,
            )
        }
        composable(AppRoute.SteamQrLogin.route) {
            SteamQrLoginScreen(
                vaultRepository = vaultRepository,
                steamSessionRepository = steamSessionRepository,
                steamQrLoginApprovalManager = steamQrLoginApprovalManager,
                refreshVersion = vaultRefreshVersion,
            )
        }
    }
}
