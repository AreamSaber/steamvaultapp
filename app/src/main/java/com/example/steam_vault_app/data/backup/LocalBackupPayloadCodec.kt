package com.example.steam_vault_app.data.backup

import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.CloudBackupProfileSnapshot
import com.example.steam_vault_app.domain.model.LocalBackupPackage
import com.example.steam_vault_app.domain.model.LocalBackupMetadataSummary
import com.example.steam_vault_app.domain.model.LocalBackupTokenSummary
import com.example.steam_vault_app.domain.model.SteamGuardDataProfileSnapshot
import com.example.steam_vault_app.domain.model.SteamSessionProfileSnapshot
import com.example.steam_vault_app.domain.security.MasterPasswordBackupSnapshot
import org.json.JSONArray
import org.json.JSONObject

object LocalBackupPayloadCodec {
    fun encode(backupPackage: LocalBackupPackage): String {
        return JSONObject()
            .put("version", backupPackage.version)
            .put("exported_at", backupPackage.exportedAt)
            .put("encrypted_vault_json", JSONObject(backupPackage.encryptedVaultJson))
            .put(
                "security_snapshot",
                JSONObject()
                    .put(
                        "master_password_salt_base64",
                        backupPackage.securitySnapshot.masterPasswordSaltBase64,
                    )
                    .put(
                        "master_password_hash_base64",
                        backupPackage.securitySnapshot.masterPasswordHashBase64,
                    )
                    .put(
                        "master_password_kdf_name",
                        backupPackage.securitySnapshot.masterPasswordKdfName,
                    )
                    .put(
                        "master_password_iterations",
                        backupPackage.securitySnapshot.masterPasswordIterations,
                    )
                    .put(
                        "master_password_memory_kib",
                        backupPackage.securitySnapshot.masterPasswordMemoryKiB,
                    )
                    .put(
                        "master_password_parallelism",
                        backupPackage.securitySnapshot.masterPasswordParallelism,
                    )
                    .put(
                        "master_password_version",
                        backupPackage.securitySnapshot.masterPasswordVersion,
                    )
                    .put(
                        "vault_key_salt_base64",
                        backupPackage.securitySnapshot.vaultKeySaltBase64,
                    )
                    .put(
                        "wrapped_vault_key_json",
                        JSONObject(backupPackage.securitySnapshot.wrappedVaultKeyJson),
                    ),
            )
            .apply {
                backupPackage.steamSessionProfileSnapshot?.let { snapshot ->
                    put(
                        "steam_session_profile",
                        JSONObject()
                            .put("encrypted_profile_json", snapshot.encryptedProfileJson),
                    )
                }
                backupPackage.steamGuardDataProfileSnapshot?.let { snapshot ->
                    put(
                        "steam_guard_data_profile",
                        JSONObject()
                            .put("encrypted_profile_json", snapshot.encryptedProfileJson),
                    )
                }
                backupPackage.cloudBackupProfileSnapshot?.let { snapshot ->
                    put(
                        "cloud_backup_profile",
                        JSONObject()
                            .put("encrypted_profile_json", snapshot.encryptedProfileJson),
                    )
                }
                backupPackage.appSecuritySettings?.let { settings ->
                    put(
                        "app_security_settings",
                        JSONObject()
                            .put("secure_screens_enabled", settings.secureScreensEnabled)
                            .put(
                                "biometric_quick_unlock_enabled",
                                settings.biometricQuickUnlockEnabled,
                            )
                            .put(
                                "auto_lock_timeout",
                                settings.autoLockTimeout.preferenceValue,
                            ),
                    )
                }
                backupPackage.metadataSummary?.let { summary ->
                    put("metadata_summary", encodeMetadataSummary(summary))
                }
            }
            .toString(2)
    }

    fun decode(rawPayload: String): LocalBackupPackage {
        val json = JSONObject(rawPayload)
        val securitySnapshotJson = json.getJSONObject("security_snapshot")
        val steamSessionProfileJson = json.optJSONObject("steam_session_profile")
        val steamGuardDataProfileJson = json.optJSONObject("steam_guard_data_profile")
        val cloudBackupProfileJson = json.optJSONObject("cloud_backup_profile")
        val appSecuritySettingsJson = json.optJSONObject("app_security_settings")
        val metadataSummaryJson = json.optJSONObject("metadata_summary")

        return LocalBackupPackage(
            version = json.getInt("version"),
            exportedAt = json.getString("exported_at"),
            encryptedVaultJson = json.getJSONObject("encrypted_vault_json").toString(),
            securitySnapshot = MasterPasswordBackupSnapshot(
                masterPasswordSaltBase64 = securitySnapshotJson.getString(
                    "master_password_salt_base64",
                ),
                masterPasswordHashBase64 = securitySnapshotJson.getString(
                    "master_password_hash_base64",
                ),
                masterPasswordKdfName = securitySnapshotJson.optString(
                    "master_password_kdf_name",
                    "pbkdf2",
                ),
                masterPasswordIterations = securitySnapshotJson.getInt(
                    "master_password_iterations",
                ),
                masterPasswordMemoryKiB = securitySnapshotJson.optInt(
                    "master_password_memory_kib",
                    0,
                ),
                masterPasswordParallelism = securitySnapshotJson.optInt(
                    "master_password_parallelism",
                    1,
                ),
                masterPasswordVersion = securitySnapshotJson.getInt(
                    "master_password_version",
                ),
                vaultKeySaltBase64 = securitySnapshotJson.getString(
                    "vault_key_salt_base64",
                ),
                wrappedVaultKeyJson = securitySnapshotJson.getJSONObject(
                    "wrapped_vault_key_json",
                ).toString(),
            ),
            steamSessionProfileSnapshot = steamSessionProfileJson?.let { snapshot ->
                SteamSessionProfileSnapshot(
                    encryptedProfileJson = if (snapshot.isNull("encrypted_profile_json")) {
                        null
                    } else {
                        snapshot.getString("encrypted_profile_json")
                    },
                )
            },
            steamGuardDataProfileSnapshot = steamGuardDataProfileJson?.let { snapshot ->
                SteamGuardDataProfileSnapshot(
                    encryptedProfileJson = if (snapshot.isNull("encrypted_profile_json")) {
                        null
                    } else {
                        snapshot.getString("encrypted_profile_json")
                    },
                )
            },
            cloudBackupProfileSnapshot = cloudBackupProfileJson?.let { snapshot ->
                CloudBackupProfileSnapshot(
                    encryptedProfileJson = if (snapshot.isNull("encrypted_profile_json")) {
                        null
                    } else {
                        snapshot.getString("encrypted_profile_json")
                    },
                )
            },
            appSecuritySettings = appSecuritySettingsJson?.let { settings ->
                AppSecuritySettings(
                    secureScreensEnabled = settings.optBoolean("secure_screens_enabled", true),
                    biometricQuickUnlockEnabled = settings.optBoolean(
                        "biometric_quick_unlock_enabled",
                        false,
                    ),
                    autoLockTimeout = AutoLockTimeoutOption.fromPreferenceValue(
                        settings.optString(
                            "auto_lock_timeout",
                            AutoLockTimeoutOption.default.preferenceValue,
                        ),
                    ),
                )
            },
            metadataSummary = metadataSummaryJson?.let(::decodeMetadataSummary),
        )
    }

    private fun encodeMetadataSummary(summary: LocalBackupMetadataSummary): JSONObject {
        return JSONObject()
            .put("token_count", summary.tokenCount)
            .put("steam_session_count", summary.steamSessionCount)
            .put("steam_guard_data_count", summary.steamGuardDataCount)
            .put("tokens_with_session_count", summary.tokensWithSessionCount)
            .put("tokens_with_guard_data_count", summary.tokensWithGuardDataCount)
            .put(
                "confirmation_material_token_count",
                summary.confirmationMaterialTokenCount,
            )
            .put(
                "token_summaries",
                JSONArray().apply {
                    summary.tokenSummaries.forEach { token ->
                        put(
                            JSONObject()
                                .put("account_name_hint", token.accountNameHint)
                                .put(
                                    "account_name_fingerprint",
                                    token.accountNameFingerprint,
                                )
                                .put("steam_id_hint", token.steamIdHint)
                                .put("steam_id_fingerprint", token.steamIdFingerprint)
                                .put("platform", token.platform)
                                .put("session_platform", token.sessionPlatform)
                                .put("has_identity_secret", token.hasIdentitySecret)
                                .put("has_device_id", token.hasDeviceId)
                                .put("has_revocation_code", token.hasRevocationCode)
                                .put("has_uri", token.hasUri)
                                .put("has_session", token.hasSession)
                                .put("has_session_id", token.hasSessionId)
                                .put("has_login_cookie", token.hasLoginCookie)
                                .put("has_refresh_token", token.hasRefreshToken)
                                .put("has_guard_data", token.hasGuardData)
                                .put(
                                    "has_confirmation_material",
                                    token.hasConfirmationMaterial,
                                ),
                        )
                    }
                },
            )
    }

    private fun decodeMetadataSummary(json: JSONObject): LocalBackupMetadataSummary {
        val tokenSummaries = buildList {
            val tokenSummariesJson = json.optJSONArray("token_summaries") ?: JSONArray()
            for (index in 0 until tokenSummariesJson.length()) {
                val tokenJson = tokenSummariesJson.getJSONObject(index)
                add(
                    LocalBackupTokenSummary(
                        accountNameHint = tokenJson.optString("account_name_hint"),
                        accountNameFingerprint = tokenJson.optString(
                            "account_name_fingerprint",
                        ),
                        steamIdHint = tokenJson.optString("steam_id_hint")
                            .takeIf { it.isNotBlank() },
                        steamIdFingerprint = tokenJson.optString("steam_id_fingerprint")
                            .takeIf { it.isNotBlank() },
                        platform = tokenJson.optString("platform", "steam"),
                        sessionPlatform = tokenJson.optString("session_platform")
                            .takeIf { it.isNotBlank() },
                        hasIdentitySecret = tokenJson.optBoolean(
                            "has_identity_secret",
                            false,
                        ),
                        hasDeviceId = tokenJson.optBoolean("has_device_id", false),
                        hasRevocationCode = tokenJson.optBoolean(
                            "has_revocation_code",
                            false,
                        ),
                        hasUri = tokenJson.optBoolean("has_uri", false),
                        hasSession = tokenJson.optBoolean("has_session", false),
                        hasSessionId = tokenJson.optBoolean("has_session_id", false),
                        hasLoginCookie = tokenJson.optBoolean(
                            "has_login_cookie",
                            false,
                        ),
                        hasRefreshToken = tokenJson.optBoolean(
                            "has_refresh_token",
                            false,
                        ),
                        hasGuardData = tokenJson.optBoolean("has_guard_data", false),
                        hasConfirmationMaterial = tokenJson.optBoolean(
                            "has_confirmation_material",
                            false,
                        ),
                    ),
                )
            }
        }

        return LocalBackupMetadataSummary(
            tokenCount = json.optInt("token_count", tokenSummaries.size),
            steamSessionCount = json.optInt("steam_session_count", 0),
            steamGuardDataCount = json.optInt("steam_guard_data_count", 0),
            tokensWithSessionCount = json.optInt("tokens_with_session_count", 0),
            tokensWithGuardDataCount = json.optInt("tokens_with_guard_data_count", 0),
            confirmationMaterialTokenCount = json.optInt(
                "confirmation_material_token_count",
                0,
            ),
            tokenSummaries = tokenSummaries,
        )
    }
}
