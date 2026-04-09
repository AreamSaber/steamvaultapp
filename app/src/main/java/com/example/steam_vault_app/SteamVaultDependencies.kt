package com.example.steam_vault_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.data.cloudbackup.CompositeAutoCloudBackupScheduler
import com.example.steam_vault_app.data.cloudbackup.InProcessAutoCloudBackupScheduler
import com.example.steam_vault_app.data.cloudbackup.OkHttpWebDavClient
import com.example.steam_vault_app.data.cloudbackup.SystemAutoCloudBackupScheduler
import com.example.steam_vault_app.data.cloudbackup.WebDavCloudBackupSyncManager
import com.example.steam_vault_app.data.cloudbackup.WorkManagerBackgroundCloudBackupWorkDispatcher
import com.example.steam_vault_app.data.importing.SteamImportParser
import com.example.steam_vault_app.data.repository.LocalCloudBackupRepository
import com.example.steam_vault_app.data.repository.LocalSteamGuardDataRepository
import com.example.steam_vault_app.data.repository.LocalSteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.data.repository.LocalSteamAuthenticatorBindingProgressRepository
import com.example.steam_vault_app.data.repository.LocalSecuritySettingsRepository
import com.example.steam_vault_app.data.repository.LocalSteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.data.repository.LocalSteamSessionRepository
import com.example.steam_vault_app.data.repository.LocalSteamTimeRepository
import com.example.steam_vault_app.data.repository.LocalVaultRepository
import com.example.steam_vault_app.data.security.LocalVaultCryptography
import com.example.steam_vault_app.data.security.LocalMasterPasswordManager
import com.example.steam_vault_app.data.steam.DefaultSteamConfirmationSyncManager
import com.example.steam_vault_app.data.steam.DefaultSteamQrLoginApprovalManager
import com.example.steam_vault_app.data.steam.DefaultSteamSessionValidationSyncManager
import com.example.steam_vault_app.data.steam.DefaultSteamTimeSyncManager
import com.example.steam_vault_app.data.steam.OkHttpSteamProtocolLoginRepository
import com.example.steam_vault_app.data.steam.OkHttpSteamAuthenticatorBindingApiClient
import com.example.steam_vault_app.data.steam.OkHttpSteamConfirmationApiClient
import com.example.steam_vault_app.data.steam.OkHttpSteamSessionValidationApiClient
import com.example.steam_vault_app.data.steam.OkHttpSteamTimeApiClient
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingApiClient
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.repository.SteamGuardDataRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingProgressRepository
import com.example.steam_vault_app.domain.repository.SecuritySettingsRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.domain.repository.SteamProtocolLoginRepository
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.SteamTimeRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import com.example.steam_vault_app.domain.sync.SteamConfirmationSyncManager
import com.example.steam_vault_app.domain.sync.SteamQrLoginApprovalManager
import com.example.steam_vault_app.domain.sync.SteamSessionValidationSyncManager
import com.example.steam_vault_app.domain.sync.SteamTimeSyncManager

data class SteamVaultDependencies(
    val masterPasswordManager: MasterPasswordManager,
    val vaultRepository: VaultRepository,
    val cloudBackupRepository: CloudBackupRepository,
    val securitySettingsRepository: SecuritySettingsRepository,
    val steamAuthenticatorBindingContextRepository: SteamAuthenticatorBindingContextRepository,
    val steamAuthenticatorBindingProgressRepository: SteamAuthenticatorBindingProgressRepository,
    val steamAuthenticatorEnrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    val steamGuardDataRepository: SteamGuardDataRepository,
    val steamProtocolLoginRepository: SteamProtocolLoginRepository,
    val steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    val steamSessionRepository: SteamSessionRepository,
    val steamTimeRepository: SteamTimeRepository,
    val cloudBackupSyncManager: CloudBackupSyncManager,
    val steamAuthenticatorBindingApiClient: SteamAuthenticatorBindingApiClient,
    val steamConfirmationSyncManager: SteamConfirmationSyncManager,
    val steamQrLoginApprovalManager: SteamQrLoginApprovalManager,
    val steamSessionValidationSyncManager: SteamSessionValidationSyncManager,
    val steamTimeSyncManager: SteamTimeSyncManager,
    val vaultCryptography: VaultCryptography,
    val importParser: SteamImportParser,
    val autoCloudBackupScheduler: AutoCloudBackupScheduler,
)

@Composable
fun rememberSteamVaultDependencies(): SteamVaultDependencies {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        DataMessageCatalog.initialize(context)
        val masterPasswordManager = LocalMasterPasswordManager(context)
        val vaultCryptography = LocalVaultCryptography(context)
        var liveAutoCloudBackupScheduler: AutoCloudBackupScheduler? = null
        val autoCloudBackupScheduler = object : AutoCloudBackupScheduler {
            override fun schedule(reason: com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason) {
                liveAutoCloudBackupScheduler?.schedule(reason)
            }

            override suspend fun cancelPendingUploadsForManualRestore() {
                liveAutoCloudBackupScheduler?.cancelPendingUploadsForManualRestore()
            }
        }
        val vaultRepository = LocalVaultRepository(
            context = context,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        val cloudBackupRepository = LocalCloudBackupRepository(
            context = context,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        val securitySettingsRepository = LocalSecuritySettingsRepository(
            context = context,
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        val steamAuthenticatorBindingProgressRepository =
            LocalSteamAuthenticatorBindingProgressRepository(
                context = context,
                masterPasswordManager = masterPasswordManager,
                vaultCryptography = vaultCryptography,
            )
        val steamAuthenticatorBindingContextRepository =
            LocalSteamAuthenticatorBindingContextRepository(
                context = context,
                masterPasswordManager = masterPasswordManager,
                vaultCryptography = vaultCryptography,
            )
        val steamAuthenticatorEnrollmentDraftRepository =
            LocalSteamAuthenticatorEnrollmentDraftRepository(
                context = context,
                masterPasswordManager = masterPasswordManager,
                vaultCryptography = vaultCryptography,
            )
        val steamSessionRepository = LocalSteamSessionRepository(
            context = context,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        val steamGuardDataRepository = LocalSteamGuardDataRepository(
            context = context,
            masterPasswordManager = masterPasswordManager,
            vaultCryptography = vaultCryptography,
            autoCloudBackupScheduler = autoCloudBackupScheduler,
        )
        val steamProtocolLoginRepository = OkHttpSteamProtocolLoginRepository()
        val steamProtocolLoginOrchestrator = SteamProtocolLoginOrchestrator(
            steamProtocolLoginRepository = steamProtocolLoginRepository,
            steamSessionRepository = steamSessionRepository,
            steamGuardDataRepository = steamGuardDataRepository,
        )
        val steamTimeRepository = LocalSteamTimeRepository(context)
        val webDavClient = OkHttpWebDavClient(context = context)
        val steamAuthenticatorBindingApiClient = OkHttpSteamAuthenticatorBindingApiClient(context = context)
        val steamConfirmationApiClient = OkHttpSteamConfirmationApiClient(context = context)
        val steamSessionValidationApiClient = OkHttpSteamSessionValidationApiClient(context = context)
        val steamTimeApiClient = OkHttpSteamTimeApiClient(context = context)
        val cloudBackupSyncManager = WebDavCloudBackupSyncManager(
            cloudBackupRepository = cloudBackupRepository,
            vaultRepository = vaultRepository,
            webDavClient = webDavClient,
            context = context,
        )
        val inProcessAutoCloudBackupScheduler = InProcessAutoCloudBackupScheduler(
            cloudBackupRepository = cloudBackupRepository,
            cloudBackupSyncManager = cloudBackupSyncManager,
        )
        val systemAutoCloudBackupScheduler = SystemAutoCloudBackupScheduler(
            cloudBackupRepository = cloudBackupRepository,
            dispatcher = WorkManagerBackgroundCloudBackupWorkDispatcher(context),
        )
        val combinedAutoCloudBackupScheduler = CompositeAutoCloudBackupScheduler(
            inProcessAutoCloudBackupScheduler,
            systemAutoCloudBackupScheduler,
        )
        liveAutoCloudBackupScheduler = combinedAutoCloudBackupScheduler
        val steamConfirmationSyncManager = DefaultSteamConfirmationSyncManager(
            vaultRepository = vaultRepository,
            steamSessionRepository = steamSessionRepository,
            steamTimeRepository = steamTimeRepository,
            apiClient = steamConfirmationApiClient,
            steamProtocolLoginOrchestrator = steamProtocolLoginOrchestrator,
            context = context,
        )
        val steamQrLoginApprovalManager = DefaultSteamQrLoginApprovalManager()
        val steamSessionValidationSyncManager = DefaultSteamSessionValidationSyncManager(
            steamSessionRepository = steamSessionRepository,
            apiClient = steamSessionValidationApiClient,
            context = context,
        )
        SteamVaultDependencies(
            masterPasswordManager = masterPasswordManager,
            vaultRepository = vaultRepository,
            cloudBackupRepository = cloudBackupRepository,
            securitySettingsRepository = securitySettingsRepository,
            steamAuthenticatorBindingContextRepository =
                steamAuthenticatorBindingContextRepository,
            steamAuthenticatorBindingProgressRepository = steamAuthenticatorBindingProgressRepository,
            steamAuthenticatorEnrollmentDraftRepository = steamAuthenticatorEnrollmentDraftRepository,
            steamGuardDataRepository = steamGuardDataRepository,
            steamProtocolLoginRepository = steamProtocolLoginRepository,
            steamProtocolLoginOrchestrator = steamProtocolLoginOrchestrator,
            steamSessionRepository = steamSessionRepository,
            steamTimeRepository = steamTimeRepository,
            cloudBackupSyncManager = cloudBackupSyncManager,
            steamAuthenticatorBindingApiClient = steamAuthenticatorBindingApiClient,
            steamConfirmationSyncManager = steamConfirmationSyncManager,
            steamQrLoginApprovalManager = steamQrLoginApprovalManager,
            steamSessionValidationSyncManager = steamSessionValidationSyncManager,
            steamTimeSyncManager = DefaultSteamTimeSyncManager(
                steamTimeRepository = steamTimeRepository,
                steamTimeApiClient = steamTimeApiClient,
                context = context,
            ),
            vaultCryptography = vaultCryptography,
            importParser = SteamImportParser(context),
            autoCloudBackupScheduler = combinedAutoCloudBackupScheduler,
        )
    }
}
