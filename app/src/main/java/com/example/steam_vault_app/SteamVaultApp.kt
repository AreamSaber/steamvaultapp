package com.example.steam_vault_app

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamTimeSyncStatus
import com.example.steam_vault_app.domain.security.AutoLockPolicy
import com.example.steam_vault_app.feature.importtoken.ImportTokenEntryContext
import com.example.steam_vault_app.feature.steamqrlogin.SteamQrLoginLinkManager
import com.example.steam_vault_app.feature.steamsession.SteamSessionEntryContext
import com.example.steam_vault_app.feature.steamsession.SteamSessionEntryContextStore
import com.example.steam_vault_app.navigation.AppRoute
import com.example.steam_vault_app.navigation.AppRouteAccessPolicy
import com.example.steam_vault_app.navigation.SteamVaultNavHost
import com.example.steam_vault_app.platform.security.AndroidBiometricPromptController
import com.example.steam_vault_app.platform.security.BiometricAuthException
import com.example.steam_vault_app.platform.security.BiometricAvailability
import com.example.steam_vault_app.platform.security.WindowSecurityController
import com.example.steam_vault_app.ui.common.VaultInfoPill
import com.example.steam_vault_app.ui.theme.SteamVaultTheme
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@Composable
fun SteamVaultApp(
    windowSecurityController: WindowSecurityController,
    modifier: Modifier = Modifier,
) {
    SteamVaultTheme {
        val context = LocalContext.current
        val biometricActivity = context as? FragmentActivity
        val dependencies = rememberSteamVaultDependencies()
        val biometricPromptController = remember(biometricActivity) {
            biometricActivity?.let(::AndroidBiometricPromptController)
        }
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = AppRoute.fromRoute(navBackStackEntry?.destination?.route)
        val pendingSteamQrLoginUrl by SteamQrLoginLinkManager.pendingChallengeUrl.collectAsState()
        val scope = rememberCoroutineScope()

        var isSubmittingPasswordAction by rememberSaveable { mutableStateOf(false) }
        var passwordConfigured by rememberSaveable { mutableStateOf<Boolean?>(null) }
        var isSessionUnlocked by rememberSaveable { mutableStateOf(false) }
        var passwordCreationError by rememberSaveable { mutableStateOf<String?>(null) }
        var passwordChangeError by rememberSaveable { mutableStateOf<String?>(null) }
        var unlockError by rememberSaveable { mutableStateOf<String?>(null) }
        var isSubmittingImport by rememberSaveable { mutableStateOf(false) }
        var importError by rememberSaveable { mutableStateOf<String?>(null) }
        var vaultRefreshVersion by rememberSaveable { mutableStateOf(0) }
        var securitySettings by remember { mutableStateOf(AppSecuritySettings()) }
        var backgroundedAtElapsedMillis by remember { mutableStateOf<Long?>(null) }
        var hasBiometricQuickUnlock by remember { mutableStateOf(false) }
        var biometricAvailability by remember { mutableStateOf(BiometricAvailability(false, null)) }
        var biometricStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
        var securityStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
        var steamTimeSyncState by remember { mutableStateOf(SteamTimeSyncState()) }
        var isSyncingSteamTime by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(
            dependencies.masterPasswordManager,
            dependencies.securitySettingsRepository,
            dependencies.steamTimeRepository,
            biometricPromptController,
        ) {
            passwordConfigured = dependencies.masterPasswordManager.isMasterPasswordConfigured()
            isSessionUnlocked = dependencies.masterPasswordManager.getActiveVaultKeyMaterial() != null
            val storedSettings = dependencies.securitySettingsRepository.getSettings()
            val storedBiometricQuickUnlock = dependencies.masterPasswordManager.hasBiometricQuickUnlock()
            securitySettings = if (storedSettings.biometricQuickUnlockEnabled && !storedBiometricQuickUnlock) {
                storedSettings.copy(biometricQuickUnlockEnabled = false).also {
                    dependencies.securitySettingsRepository.saveSettings(it)
                }
            } else {
                storedSettings
            }
            hasBiometricQuickUnlock = storedBiometricQuickUnlock
            biometricAvailability = biometricPromptController?.checkAvailability()
                ?: BiometricAvailability(
                    available = false,
                    message = context.getString(R.string.biometric_unavailable_current_screen),
                )
            steamTimeSyncState = dependencies.steamTimeRepository.getState()
        }

        suspend fun persistSecuritySettings(nextSettings: AppSecuritySettings) {
            securityStatusMessage = null
            securitySettings = nextSettings
            dependencies.securitySettingsRepository.saveSettings(nextSettings)
        }

        fun requestVaultLock(message: String? = null) {
            scope.launch {
                dependencies.masterPasswordManager.clearUnlockedSession()
                passwordConfigured = true
                isSessionUnlocked = false
                passwordCreationError = null
                passwordChangeError = null
                importError = null
                backgroundedAtElapsedMillis = null
                securityStatusMessage = null
                unlockError = message
                navController.navigate(AppRoute.Unlock.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }

        fun refreshBiometricAvailability() {
            biometricAvailability = biometricPromptController?.checkAvailability()
                ?: BiometricAvailability(
                    available = false,
                    message = context.getString(R.string.biometric_unavailable_current_screen),
                )
        }

        fun configureBiometricQuickUnlock(enabled: Boolean) {
            scope.launch {
                securityStatusMessage = null
                refreshBiometricAvailability()
                if (!enabled) {
                    dependencies.masterPasswordManager.clearBiometricQuickUnlock()
                    hasBiometricQuickUnlock = false
                    persistSecuritySettings(
                        securitySettings.copy(biometricQuickUnlockEnabled = false),
                    )
                    biometricStatusMessage = context.getString(R.string.biometric_quick_unlock_disabled)
                    return@launch
                }

                if (!isSessionUnlocked) {
                    biometricStatusMessage = context.getString(R.string.biometric_enable_requires_unlock)
                    return@launch
                }

                val promptController = biometricPromptController
                if (promptController == null) {
                    biometricStatusMessage = context.getString(R.string.biometric_unavailable_current_screen)
                    return@launch
                }

                if (!biometricAvailability.available) {
                    biometricStatusMessage = biometricAvailability.message
                    return@launch
                }

                try {
                    val cipher = dependencies.masterPasswordManager.prepareBiometricEnrollmentCipher()
                    val authenticatedCipher = promptController.authenticateCipher(
                        title = context.getString(R.string.biometric_enable_prompt_title),
                        subtitle = context.getString(R.string.biometric_enable_prompt_subtitle),
                        cipher = cipher,
                    )
                    dependencies.masterPasswordManager.enableBiometricQuickUnlock(authenticatedCipher)
                    hasBiometricQuickUnlock = true
                    persistSecuritySettings(
                        securitySettings.copy(biometricQuickUnlockEnabled = true),
                    )
                    biometricStatusMessage = context.getString(R.string.biometric_quick_unlock_enabled)
                } catch (error: BiometricAuthException) {
                    if (!error.isUserCanceled) {
                        biometricStatusMessage = error.message
                    }
                } catch (_: Exception) {
                    biometricStatusMessage = context.getString(R.string.biometric_quick_unlock_enable_failed)
                }
            }
        }

        fun syncSteamTime() {
            scope.launch {
                isSyncingSteamTime = true
                steamTimeSyncState = steamTimeSyncState.copy(
                    status = SteamTimeSyncStatus.SYNCING,
                    lastErrorMessage = null,
                )
                steamTimeSyncState = dependencies.steamTimeSyncManager.syncNow()
                isSyncingSteamTime = false
            }
        }

        DisposableEffect(
            currentRoute.isSensitive,
            securitySettings.secureScreensEnabled,
            windowSecurityController,
        ) {
            windowSecurityController.setSensitiveContentProtection(
                currentRoute.isSensitive && securitySettings.secureScreensEnabled,
            )
            onDispose { }
        }

        DisposableEffect(
            isSessionUnlocked,
            passwordConfigured,
            securitySettings.autoLockTimeout,
        ) {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        backgroundedAtElapsedMillis = if (
                            passwordConfigured == true &&
                            isSessionUnlocked &&
                            securitySettings.autoLockTimeout.timeoutMillis != null
                        ) {
                            SystemClock.elapsedRealtime()
                        } else {
                            null
                        }
                    }

                    Lifecycle.Event.ON_START -> {
                        if (
                            passwordConfigured == true &&
                            isSessionUnlocked &&
                            AutoLockPolicy.shouldLockOnForegroundResume(
                                timeoutMillis = securitySettings.autoLockTimeout.timeoutMillis,
                                backgroundedAtMillis = backgroundedAtElapsedMillis,
                                resumedAtMillis = SystemClock.elapsedRealtime(),
                            )
                        ) {
                            requestVaultLock(context.getString(R.string.auto_lock_relocked_message))
                        } else {
                            backgroundedAtElapsedMillis = null
                        }
                    }

                    else -> Unit
                }
            }

            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(passwordConfigured, isSessionUnlocked, currentRoute) {
            when {
                passwordConfigured == false &&
                    !AppRouteAccessPolicy.canAccessBeforePasswordSetup(currentRoute) -> {
                    navController.navigate(AppRoute.Welcome.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }

                passwordConfigured == true &&
                    !isSessionUnlocked &&
                    !AppRouteAccessPolicy.canAccessWhileLocked(currentRoute) -> {
                    navController.navigate(AppRoute.Unlock.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }

        LaunchedEffect(
            pendingSteamQrLoginUrl,
            passwordConfigured,
            isSessionUnlocked,
            currentRoute,
        ) {
            if (
                passwordConfigured == true &&
                isSessionUnlocked &&
                !pendingSteamQrLoginUrl.isNullOrBlank() &&
                currentRoute != AppRoute.SteamQrLogin
            ) {
                navController.navigate(AppRoute.SteamQrLogin.route) {
                    launchSingleTop = true
                }
            }
        }

        val startDestination = when (passwordConfigured) {
            null -> null
            false -> AppRoute.Welcome.route
            true -> if (isSessionUnlocked) AppRoute.Tokens.route else AppRoute.Unlock.route
        }

        val showBiometricUnlockOnUnlockScreen =
            passwordConfigured == true &&
                !isSessionUnlocked &&
                securitySettings.biometricQuickUnlockEnabled &&
                hasBiometricQuickUnlock &&
                biometricAvailability.available

        if (startDestination == null) {
            LoadingAppShell(modifier = modifier)
            return@SteamVaultTheme
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (currentRoute != AppRoute.Welcome) {
                    SteamVaultTopBar(
                        canNavigateBack = navController.previousBackStackEntry != null &&
                            currentRoute !in AppRoute.bottomNavigationRoutes,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            },
            bottomBar = {
                if (currentRoute in AppRoute.bottomNavigationRoutes) {
                    SteamVaultBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            SteamVaultNavHost(
                navController = navController,
                startDestination = startDestination,
                vaultRepository = dependencies.vaultRepository,
                cloudBackupRepository = dependencies.cloudBackupRepository,
                steamAuthenticatorBindingContextRepository =
                    dependencies.steamAuthenticatorBindingContextRepository,
                steamAuthenticatorBindingProgressRepository =
                    dependencies.steamAuthenticatorBindingProgressRepository,
                steamAuthenticatorEnrollmentDraftRepository =
                    dependencies.steamAuthenticatorEnrollmentDraftRepository,
                steamProtocolLoginOrchestrator = dependencies.steamProtocolLoginOrchestrator,
                steamSessionRepository = dependencies.steamSessionRepository,
                importParser = dependencies.importParser,
                cloudBackupSyncManager = dependencies.cloudBackupSyncManager,
                steamAuthenticatorBindingApiClient = dependencies.steamAuthenticatorBindingApiClient,
                steamConfirmationSyncManager = dependencies.steamConfirmationSyncManager,
                steamQrLoginApprovalManager = dependencies.steamQrLoginApprovalManager,
                steamSessionValidationSyncManager = dependencies.steamSessionValidationSyncManager,
                vaultCryptography = dependencies.vaultCryptography,
                securitySettings = securitySettings,
                steamTimeSyncState = steamTimeSyncState,
                isSyncingSteamTime = isSyncingSteamTime,
                vaultRefreshVersion = vaultRefreshVersion,
                onCreatePassword = { rawPassword ->
                    scope.launch {
                        isSubmittingPasswordAction = true
                        passwordCreationError = null
                        passwordChangeError = null

                        val passwordChars = rawPassword.toCharArray()
                        try {
                            dependencies.masterPasswordManager.createMasterPassword(passwordChars)
                            dependencies.vaultRepository.initializeEmptyVault()
                            passwordConfigured = true
                            isSessionUnlocked = true
                            backgroundedAtElapsedMillis = null
                            unlockError = null
                            securityStatusMessage = null

                            navController.navigate(AppRoute.Tokens.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        } catch (error: IllegalArgumentException) {
                            passwordCreationError =
                                error.message ?: context.getString(R.string.create_password_save_failed)
                        } catch (_: Exception) {
                            passwordCreationError = context.getString(R.string.create_password_save_local_failed)
                        } finally {
                            passwordChars.fill('\u0000')
                            isSubmittingPasswordAction = false
                        }
                    }
                },
                onChangePassword = { rawPassword ->
                    scope.launch {
                        isSubmittingPasswordAction = true
                        passwordChangeError = null
                        securityStatusMessage = null

                        val passwordChars = rawPassword.toCharArray()
                        try {
                            dependencies.masterPasswordManager.changeMasterPassword(passwordChars)

                            val biometricResetRequired =
                                securitySettings.biometricQuickUnlockEnabled || hasBiometricQuickUnlock
                            if (securitySettings.biometricQuickUnlockEnabled) {
                                val updatedSettings = securitySettings.copy(
                                    biometricQuickUnlockEnabled = false,
                                )
                                securitySettings = updatedSettings
                                runCatching {
                                    dependencies.securitySettingsRepository.saveSettings(updatedSettings)
                                }
                            }
                            hasBiometricQuickUnlock = false
                            biometricStatusMessage = null
                            unlockError = null

                            dependencies.autoCloudBackupScheduler.schedule(
                                CloudBackupAutoBackupReason.MASTER_PASSWORD_CHANGED,
                            )

                            securityStatusMessage = context.getString(
                                if (biometricResetRequired) {
                                    R.string.master_password_change_success_biometric_disabled
                                } else {
                                    R.string.master_password_change_success
                                },
                            )
                            navController.popBackStack()
                        } catch (error: IllegalArgumentException) {
                            passwordChangeError =
                                error.message ?: context.getString(R.string.master_password_change_save_failed)
                        } catch (error: Exception) {
                            passwordChangeError =
                                error.message ?: context.getString(R.string.master_password_change_save_failed)
                        } finally {
                            passwordChars.fill('\u0000')
                            isSubmittingPasswordAction = false
                        }
                    }
                },
                onUnlock = { rawPassword ->
                    scope.launch {
                        isSubmittingPasswordAction = true
                        unlockError = null
                        passwordChangeError = null

                        val passwordChars = rawPassword.toCharArray()
                        try {
                            val unlocked = dependencies.masterPasswordManager.unlock(passwordChars)
                            if (unlocked) {
                                dependencies.vaultRepository.initializeEmptyVault()
                                isSessionUnlocked = true
                                backgroundedAtElapsedMillis = null
                                biometricStatusMessage = null
                                navController.navigate(AppRoute.Tokens.route) {
                                    popUpTo(AppRoute.Unlock.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                unlockError =
                                    context.getString(R.string.unlock_password_mismatch)
                            }
                        } catch (_: Exception) {
                            unlockError = context.getString(R.string.unlock_temporary_failed)
                        } finally {
                            passwordChars.fill('\u0000')
                            isSubmittingPasswordAction = false
                        }
                    }
                },
                onUnlockWithBiometric = {
                    scope.launch {
                        isSubmittingPasswordAction = true
                        unlockError = null
                        refreshBiometricAvailability()

                        val promptController = biometricPromptController
                        if (promptController == null) {
                            unlockError = context.getString(R.string.biometric_unavailable_current_screen)
                            isSubmittingPasswordAction = false
                            return@launch
                        }

                        if (!biometricAvailability.available) {
                            unlockError = biometricAvailability.message
                                ?: context.getString(R.string.biometric_unlock_unavailable)
                            isSubmittingPasswordAction = false
                            return@launch
                        }

                        try {
                            val cipher = dependencies.masterPasswordManager.prepareBiometricUnlockCipher()
                                ?: throw IllegalStateException(
                                    context.getString(R.string.biometric_unlock_material_unavailable),
                                )
                            val authenticatedCipher = promptController.authenticateCipher(
                                title = context.getString(R.string.biometric_unlock_prompt_title),
                                subtitle = context.getString(R.string.biometric_unlock_prompt_subtitle),
                                cipher = cipher,
                            )
                            val unlocked = dependencies.masterPasswordManager
                                .unlockWithBiometricCipher(authenticatedCipher)
                            if (unlocked) {
                                dependencies.vaultRepository.initializeEmptyVault()
                                isSessionUnlocked = true
                                backgroundedAtElapsedMillis = null
                                unlockError = null
                                navController.navigate(AppRoute.Tokens.route) {
                                    popUpTo(AppRoute.Unlock.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                unlockError = context.getString(R.string.biometric_unlock_data_unavailable)
                            }
                        } catch (error: BiometricAuthException) {
                            if (!error.isUserCanceled) {
                                unlockError = error.message
                            }
                        } catch (error: Exception) {
                            dependencies.masterPasswordManager.clearBiometricQuickUnlock()
                            hasBiometricQuickUnlock = false
                            persistSecuritySettings(
                                securitySettings.copy(biometricQuickUnlockEnabled = false),
                            )
                            unlockError = error.message
                                ?: context.getString(R.string.biometric_unlock_reenable_required)
                        } finally {
                            isSubmittingPasswordAction = false
                        }
                    }
                },
                onLockVault = {
                    requestVaultLock()
                },
                onBackupRestored = {
                    vaultRefreshVersion += 1
                    requestVaultLock(context.getString(R.string.backup_restore_relock_message))
                },
                onCloudBackupRestored = {
                    vaultRefreshVersion += 1
                    requestVaultLock(context.getString(R.string.cloud_restore_relock_message))
                },
                onAuthenticatorBound = {
                    vaultRefreshVersion += 1
                    navController.navigate(AppRoute.Tokens.route) {
                        popUpTo(AppRoute.Import.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onSecuritySettingsChanged = { nextSettings ->
                    scope.launch {
                        persistSecuritySettings(nextSettings)
                    }
                },
                onBiometricQuickUnlockToggle = ::configureBiometricQuickUnlock,
                onSyncSteamTime = ::syncSteamTime,
                biometricQuickUnlockAvailable = biometricAvailability.available,
                biometricStatusMessage = biometricStatusMessage ?: biometricAvailability.message,
                securityStatusMessage = securityStatusMessage,
                showBiometricUnlockOnUnlockScreen = showBiometricUnlockOnUnlockScreen,
                onImportToken = { rawPayload, accountName, sharedSecret, importEntryContext ->
                    scope.launch {
                        isSubmittingImport = true
                        importError = null

                        try {
                            val importDraft = dependencies.importParser.parse(
                                rawPayload = rawPayload,
                                manualAccountName = accountName,
                                manualSharedSecret = sharedSecret,
                            )
                            val token = dependencies.vaultRepository.saveImportedToken(importDraft)
                            importDraft.importedSession?.let { importedSession ->
                                dependencies.steamSessionRepository.saveSession(
                                    importedSession.toRecord(
                                        tokenId = token.id,
                                        accountName = token.accountName,
                                        updatedAt = token.updatedAt,
                                    ),
                                )
                            }
                            vaultRefreshVersion += 1

                            if (
                                importEntryContext?.kind ==
                                ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR
                            ) {
                                SteamSessionEntryContextStore.push(
                                    tokenId = token.id,
                                    entryContext = SteamSessionEntryContext
                                        .importedExistingAuthenticator(
                                            steamId = importEntryContext.steamId,
                                            accountName = token.accountName,
                                        ),
                                )
                                navController.navigate(AppRoute.SteamSession.createRoute(token.id)) {
                                    popUpTo(AppRoute.Import.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(AppRoute.Tokens.route) {
                                    popUpTo(AppRoute.Import.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        } catch (error: IllegalArgumentException) {
                            importError = error.message ?: context.getString(R.string.import_invalid_content)
                        } catch (_: Exception) {
                            importError = context.getString(R.string.import_save_failed)
                        } finally {
                            isSubmittingImport = false
                        }
                    }
                },
                isSubmittingPasswordAction = isSubmittingPasswordAction,
                isSubmittingImport = isSubmittingImport,
                passwordCreationError = passwordCreationError,
                passwordChangeError = passwordChangeError,
                unlockError = unlockError,
                importError = importError,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun LoadingAppShell(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.loading_local_vault),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SteamVaultTopBar(
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (canNavigateBack) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = canNavigateBack,
                                onClick = onNavigateBack,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (canNavigateBack) {
                                Icons.AutoMirrored.Filled.ArrowBack
                            } else {
                                Icons.Default.Lock
                            },
                            contentDescription = if (canNavigateBack) {
                                stringResource(R.string.vault_shell_back)
                            } else {
                                null
                            },
                            tint = if (canNavigateBack) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.vault_brand_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.vault_status_local_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VaultInfoPill(
                text = stringResource(R.string.vault_status_backup_ready),
            )
        }
    }
}

@Composable
private fun SteamVaultBottomBar(
    currentRoute: AppRoute,
    onNavigate: (AppRoute) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                route = AppRoute.Tokens,
                currentRoute = currentRoute,
                label = stringResource(R.string.vault_shell_nav_tokens),
                icon = Icons.Default.Lock,
                onNavigate = onNavigate,
            )
            BottomBarItem(
                route = AppRoute.Import,
                currentRoute = currentRoute,
                label = stringResource(R.string.vault_shell_nav_add),
                icon = Icons.Default.Add,
                onNavigate = onNavigate,
            )
            BottomBarItem(
                route = AppRoute.Settings,
                currentRoute = currentRoute,
                label = stringResource(R.string.vault_shell_nav_settings),
                icon = Icons.Default.Settings,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    route: AppRoute,
    currentRoute: AppRoute,
    label: String,
    icon: ImageVector,
    onNavigate: (AppRoute) -> Unit,
) {
    val selected = currentRoute == route
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onNavigate(route) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
