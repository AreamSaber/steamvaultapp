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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBackupPayloadCodecTest {
    @Test
    fun encodeThenDecode_roundTripsArgon2BackupPayload() {
        val backupPackage = LocalBackupPackage(
            version = 1,
            exportedAt = "2026-04-07T14:15:16Z",
            encryptedVaultJson = JSONObject()
                .put("version", 1)
                .put("kdf_name", "wrapped-vault-key")
                .put("cipher_name", "aes-256-gcm")
                .put("salt_base64", "")
                .put("nonce_base64", "bm9uY2U=")
                .put("ciphertext_base64", "Y2lwaGVydGV4dA==")
                .toString(),
            securitySnapshot = MasterPasswordBackupSnapshot(
                masterPasswordSaltBase64 = "c2FsdA==",
                masterPasswordHashBase64 = "aGFzaA==",
                masterPasswordKdfName = "argon2id",
                masterPasswordIterations = 3,
                masterPasswordMemoryKiB = 65_536,
                masterPasswordParallelism = 2,
                masterPasswordVersion = 3,
                vaultKeySaltBase64 = "dmF1bHQtc2FsdA==",
                wrappedVaultKeyJson = JSONObject()
                    .put("version", 1)
                    .put(
                        "password_wrapped_key",
                        JSONObject()
                            .put("cipher_name", "aes-256-gcm")
                            .put("nonce_base64", "bm9uY2U=")
                            .put("ciphertext_base64", "Y2lwaGVydGV4dA=="),
                    )
                    .toString(),
            ),
            steamSessionProfileSnapshot = SteamSessionProfileSnapshot(
                encryptedProfileJson = JSONObject()
                    .put("version", 1)
                    .put("kdf_name", "wrapped-vault-key")
                    .put("cipher_name", "aes-256-gcm")
                    .put("salt_base64", "")
                    .put("nonce_base64", "c2Vzc2lvbg==")
                    .put("ciphertext_base64", "c2Vzc2lvbi1jaXBoZXJ0ZXh0")
                    .toString(),
            ),
            steamGuardDataProfileSnapshot = SteamGuardDataProfileSnapshot(
                encryptedProfileJson = JSONObject()
                    .put("version", 1)
                    .put("kdf_name", "wrapped-vault-key")
                    .put("cipher_name", "aes-256-gcm")
                    .put("salt_base64", "")
                    .put("nonce_base64", "Z3VhcmQ=")
                    .put("ciphertext_base64", "Z3VhcmQtY2lwaGVydGV4dA==")
                    .toString(),
            ),
            cloudBackupProfileSnapshot = CloudBackupProfileSnapshot(
                encryptedProfileJson = JSONObject()
                    .put("version", 1)
                    .put("kdf_name", "wrapped-vault-key")
                    .put("cipher_name", "aes-256-gcm")
                    .put("salt_base64", "")
                    .put("nonce_base64", "Y2xvdWQ=")
                    .put("ciphertext_base64", "Y2xvdWQtY2lwaGVydGV4dA==")
                    .toString(),
            ),
            appSecuritySettings = AppSecuritySettings(
                secureScreensEnabled = false,
                biometricQuickUnlockEnabled = true,
                autoLockTimeout = AutoLockTimeoutOption.FIVE_MINUTES,
            ),
            metadataSummary = LocalBackupMetadataSummary(
                tokenCount = 2,
                steamSessionCount = 1,
                steamGuardDataCount = 1,
                tokensWithSessionCount = 1,
                tokensWithGuardDataCount = 1,
                confirmationMaterialTokenCount = 1,
                tokenSummaries = listOf(
                    LocalBackupTokenSummary(
                        accountNameHint = "yo***67",
                        accountNameFingerprint = "a1b2c3d4e5f60708",
                        steamIdHint = "76***4321",
                        steamIdFingerprint = "1029384756abcdef",
                        platform = "steam",
                        sessionPlatform = "MOBILE_APP",
                        hasIdentitySecret = true,
                        hasDeviceId = true,
                        hasRevocationCode = true,
                        hasUri = true,
                        hasSession = true,
                        hasSessionId = true,
                        hasLoginCookie = true,
                        hasRefreshToken = true,
                        hasGuardData = true,
                        hasConfirmationMaterial = true,
                    ),
                    LocalBackupTokenSummary(
                        accountNameHint = "ab***yz",
                        accountNameFingerprint = "0f1e2d3c4b5a6978",
                        steamIdHint = null,
                        steamIdFingerprint = null,
                        platform = "steam",
                        sessionPlatform = null,
                        hasIdentitySecret = true,
                        hasDeviceId = true,
                        hasRevocationCode = false,
                        hasUri = false,
                        hasSession = false,
                        hasSessionId = false,
                        hasLoginCookie = false,
                        hasRefreshToken = false,
                        hasGuardData = false,
                        hasConfirmationMaterial = false,
                    ),
                ),
            ),
        )

        val encoded = LocalBackupPayloadCodec.encode(backupPackage)
        val decoded = LocalBackupPayloadCodec.decode(encoded)
        val expectedSteamSessionProfile = requireNotNull(backupPackage.steamSessionProfileSnapshot)
        val actualSteamSessionProfile = requireNotNull(decoded.steamSessionProfileSnapshot)
        val expectedSteamGuardDataProfile = requireNotNull(backupPackage.steamGuardDataProfileSnapshot)
        val actualSteamGuardDataProfile = requireNotNull(decoded.steamGuardDataProfileSnapshot)

        assertEquals(backupPackage.version, decoded.version)
        assertEquals(backupPackage.exportedAt, decoded.exportedAt)
        assertEquals(
            JSONObject(backupPackage.encryptedVaultJson).toString(),
            JSONObject(decoded.encryptedVaultJson).toString(),
        )
        assertEquals(
            backupPackage.securitySnapshot.copy(
                wrappedVaultKeyJson = JSONObject(backupPackage.securitySnapshot.wrappedVaultKeyJson).toString(),
            ),
            decoded.securitySnapshot.copy(
                wrappedVaultKeyJson = JSONObject(decoded.securitySnapshot.wrappedVaultKeyJson).toString(),
            ),
        )
        assertEquals(
            expectedSteamSessionProfile.copy(
                encryptedProfileJson = JSONObject(
                    expectedSteamSessionProfile.encryptedProfileJson,
                ).toString(),
            ),
            actualSteamSessionProfile.copy(
                encryptedProfileJson = JSONObject(
                    actualSteamSessionProfile.encryptedProfileJson,
                ).toString(),
            ),
        )
        assertEquals(
            expectedSteamGuardDataProfile.copy(
                encryptedProfileJson = JSONObject(
                    expectedSteamGuardDataProfile.encryptedProfileJson,
                ).toString(),
            ),
            actualSteamGuardDataProfile.copy(
                encryptedProfileJson = JSONObject(
                    actualSteamGuardDataProfile.encryptedProfileJson,
                ).toString(),
            ),
        )
        assertEquals(backupPackage.cloudBackupProfileSnapshot, decoded.cloudBackupProfileSnapshot)
        assertEquals(backupPackage.appSecuritySettings, decoded.appSecuritySettings)
        assertEquals(backupPackage.metadataSummary, decoded.metadataSummary)
    }

    @Test
    fun decode_legacyPayloadFallsBackToPbkdf2Defaults() {
        val legacyPayload = """
            {
              "version": 1,
              "exported_at": "2026-04-07T14:15:16Z",
              "encrypted_vault_json": {
                "version": 1,
                "kdf_name": "wrapped-vault-key",
                "cipher_name": "aes-256-gcm",
                "salt_base64": "",
                "nonce_base64": "bm9uY2U=",
                "ciphertext_base64": "Y2lwaGVydGV4dA=="
              },
              "security_snapshot": {
                "master_password_salt_base64": "c2FsdA==",
                "master_password_hash_base64": "aGFzaA==",
                "master_password_iterations": 120000,
                "master_password_version": 2,
                "vault_key_salt_base64": "dmF1bHQtc2FsdA==",
                "wrapped_vault_key_json": {
                  "version": 1,
                  "password_wrapped_key": {
                    "cipher_name": "aes-256-gcm",
                    "nonce_base64": "bm9uY2U=",
                    "ciphertext_base64": "Y2lwaGVydGV4dA=="
                  }
                }
              }
            }
        """.trimIndent()

        val decoded = LocalBackupPayloadCodec.decode(legacyPayload)

        assertEquals("pbkdf2", decoded.securitySnapshot.masterPasswordKdfName)
        assertEquals(0, decoded.securitySnapshot.masterPasswordMemoryKiB)
        assertEquals(1, decoded.securitySnapshot.masterPasswordParallelism)
        assertNull(decoded.steamSessionProfileSnapshot)
        assertNull(decoded.steamGuardDataProfileSnapshot)
        assertNull(decoded.cloudBackupProfileSnapshot)
        assertNull(decoded.appSecuritySettings)
        assertNull(decoded.metadataSummary)
    }
}
