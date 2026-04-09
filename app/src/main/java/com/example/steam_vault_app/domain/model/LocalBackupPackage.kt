package com.example.steam_vault_app.domain.model

import com.example.steam_vault_app.domain.security.MasterPasswordBackupSnapshot

data class LocalBackupPackage(
    val version: Int,
    val exportedAt: String,
    val encryptedVaultJson: String,
    val securitySnapshot: MasterPasswordBackupSnapshot,
    val steamSessionProfileSnapshot: SteamSessionProfileSnapshot? = null,
    val steamGuardDataProfileSnapshot: SteamGuardDataProfileSnapshot? = null,
    val cloudBackupProfileSnapshot: CloudBackupProfileSnapshot? = null,
    val appSecuritySettings: AppSecuritySettings? = null,
    val metadataSummary: LocalBackupMetadataSummary? = null,
)

data class SteamSessionProfileSnapshot(
    val encryptedProfileJson: String?,
)

data class SteamGuardDataProfileSnapshot(
    val encryptedProfileJson: String?,
)

data class CloudBackupProfileSnapshot(
    val encryptedProfileJson: String?,
)

data class LocalBackupMetadataSummary(
    val tokenCount: Int,
    val steamSessionCount: Int,
    val steamGuardDataCount: Int,
    val tokensWithSessionCount: Int,
    val tokensWithGuardDataCount: Int,
    val confirmationMaterialTokenCount: Int,
    val tokenSummaries: List<LocalBackupTokenSummary>,
)

data class LocalBackupTokenSummary(
    val accountNameHint: String,
    val accountNameFingerprint: String,
    val steamIdHint: String? = null,
    val steamIdFingerprint: String? = null,
    val platform: String,
    val sessionPlatform: String? = null,
    val hasIdentitySecret: Boolean,
    val hasDeviceId: Boolean,
    val hasRevocationCode: Boolean,
    val hasUri: Boolean,
    val hasSession: Boolean,
    val hasSessionId: Boolean,
    val hasLoginCookie: Boolean,
    val hasRefreshToken: Boolean,
    val hasGuardData: Boolean,
    val hasConfirmationMaterial: Boolean,
)
