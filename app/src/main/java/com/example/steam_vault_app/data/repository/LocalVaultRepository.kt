package com.example.steam_vault_app.data.repository

import android.content.Context
import android.provider.Settings
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.cloudbackup.AutoCloudBackupScheduler
import com.example.steam_vault_app.domain.model.AppSecuritySettings
import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupProfileSnapshot
import com.example.steam_vault_app.domain.model.LocalBackupPackage
import com.example.steam_vault_app.domain.model.LocalBackupMetadataSummary
import com.example.steam_vault_app.domain.model.LocalBackupTokenSummary
import com.example.steam_vault_app.domain.model.SteamGuardDataRecord
import com.example.steam_vault_app.domain.model.SteamGuardDataProfileSnapshot
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionProfileSnapshot
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.data.security.EncryptedVaultJsonCodec
import com.example.steam_vault_app.data.steam.SteamGuardDataProfileCodec
import com.example.steam_vault_app.data.steam.SteamSessionProfileCodec
import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.model.VaultBlob
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.EncryptedVault
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.example.steam_vault_app.domain.security.VaultCryptography
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalVaultRepository(
    context: Context,
    private val masterPasswordManager: MasterPasswordManager,
    private val vaultCryptography: VaultCryptography,
    private val autoCloudBackupScheduler: AutoCloudBackupScheduler? = null,
) : VaultRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.VAULT_PREFS,
        Context.MODE_PRIVATE,
    )
    private val securityPrefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.SECURITY_PREFS,
        Context.MODE_PRIVATE,
    )
    private val cloudBackupPrefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.CLOUD_BACKUP_PREFS,
        Context.MODE_PRIVATE,
    )
    private val steamSessionPrefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_SESSION_PREFS,
        Context.MODE_PRIVATE,
    )
    private val steamProtocolLoginPrefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.STEAM_PROTOCOL_LOGIN_PREFS,
        Context.MODE_PRIVATE,
    )

    override suspend fun initializeEmptyVault() = withContext(Dispatchers.IO) {
        if (prefs.contains(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON)) {
            return@withContext
        }

        val legacyJson = prefs.getString(SteamVaultPreferenceKeys.KEY_TOKENS_JSON, null)
        val cleartextJson = legacyJson ?: JSONArray().toString()
        writeEncryptedVault(cleartextJson)
    }

    override suspend fun hasVault(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON) ||
            prefs.contains(SteamVaultPreferenceKeys.KEY_TOKENS_JSON)
    }

    override suspend fun getTokens(): List<TokenRecord> = withContext(Dispatchers.IO) {
        val tokens = mutableListOf<TokenRecord>()
        val tokenArray = readTokenArray()

        for (index in 0 until tokenArray.length()) {
            val token = jsonToToken(tokenArray.getJSONObject(index))
            if (token.deletedAt == null) {
                tokens += token
            }
        }

        tokens.sortedBy { token -> token.accountName.lowercase(Locale.US) }
    }

    override suspend fun getToken(tokenId: String): TokenRecord? = withContext(Dispatchers.IO) {
        val tokenArray = readTokenArray()
        for (index in 0 until tokenArray.length()) {
            val token = jsonToToken(tokenArray.getJSONObject(index))
            if (token.id == tokenId && token.deletedAt == null) {
                return@withContext token
            }
        }

        null
    }

    override suspend fun saveImportedToken(importDraft: ImportDraft): TokenRecord = withContext(Dispatchers.IO) {
        val tokens = readTokenArray()
        val now = nowIsoUtc()
        val token = TokenRecord(
            id = UUID.randomUUID().toString(),
            accountName = importDraft.accountName,
            sharedSecret = importDraft.sharedSecret,
            identitySecret = importDraft.identitySecret,
            serialNumber = importDraft.serialNumber,
            revocationCode = importDraft.revocationCode,
            secret1 = importDraft.secret1,
            deviceId = importDraft.deviceId,
            tokenGid = importDraft.tokenGid,
            uri = importDraft.uri,
            createdAt = now,
            updatedAt = now,
        )

        tokens.put(tokenToJson(token))
        writeTokenArray(tokens)
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        token
    }

    override suspend fun deleteToken(tokenId: String) = withContext(Dispatchers.IO) {
        val currentArray = readTokenArray()
        val nextArray = JSONArray()
        val now = nowIsoUtc()

        for (index in 0 until currentArray.length()) {
            val tokenObject = currentArray.getJSONObject(index)
            if (tokenObject.optString("id") == tokenId) {
                tokenObject.put("deleted_at", now)
                tokenObject.put("updated_at", now)
            }
            nextArray.put(tokenObject)
        }

        writeTokenArray(nextArray)
        autoCloudBackupScheduler?.schedule(CloudBackupAutoBackupReason.VAULT_CONTENT_CHANGED)
        Unit
    }

    override suspend fun exportVault(): VaultBlob = withContext(Dispatchers.IO) {
        initializeEmptyVault()
        val encryptedJson = prefs.getString(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON, null)
            ?: throw IllegalStateException(string(R.string.repository_vault_missing))
        val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedJson)

        VaultBlob(
            userId = "local-user",
            deviceId = resolveDeviceId(),
            version = encryptedVault.version,
            vaultCiphertext = encryptedVault.ciphertextBase64,
            vaultNonce = encryptedVault.nonceBase64,
            vaultKdf = JSONObject()
                .put("name", encryptedVault.kdfName)
                .put("salt", encryptedVault.saltBase64)
                .toString(),
            updatedAt = nowIsoUtc(),
        )
    }

    override suspend fun exportLocalBackup(): LocalBackupPackage = withContext(Dispatchers.IO) {
        initializeEmptyVault()
        val encryptedVaultJson = prefs.getString(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON, null)
            ?: throw IllegalStateException(string(R.string.repository_vault_missing))
        val normalizedSteamSessionProfileJson = normalizeEncryptedSnapshot(
            steamSessionPrefs.getString(
                SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
                null,
            ),
        )
        val normalizedSteamGuardDataProfileJson = normalizeEncryptedSnapshot(
            steamProtocolLoginPrefs.getString(
                SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
                null,
            ),
        )

        // Parse once so broken payloads fail before the backup file is generated.
        val normalizedEncryptedVaultJson = EncryptedVaultJsonCodec.encode(
            EncryptedVaultJsonCodec.decode(encryptedVaultJson),
        )

        LocalBackupPackage(
            version = BACKUP_FORMAT_VERSION,
            exportedAt = nowIsoUtc(),
            encryptedVaultJson = normalizedEncryptedVaultJson,
            securitySnapshot = masterPasswordManager.exportBackupSnapshot(),
            steamSessionProfileSnapshot = SteamSessionProfileSnapshot(
                encryptedProfileJson = normalizedSteamSessionProfileJson,
            ),
            steamGuardDataProfileSnapshot = SteamGuardDataProfileSnapshot(
                encryptedProfileJson = normalizedSteamGuardDataProfileJson,
            ),
            cloudBackupProfileSnapshot = CloudBackupProfileSnapshot(
                encryptedProfileJson = cloudBackupPrefs.getString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
                    null,
                ),
            ),
            appSecuritySettings = readAppSecuritySettings(),
            metadataSummary = buildBackupMetadataSummary(
                normalizedSteamSessionProfileJson = normalizedSteamSessionProfileJson,
                normalizedSteamGuardDataProfileJson = normalizedSteamGuardDataProfileJson,
            ),
        )
    }

    override suspend fun restoreLocalBackup(
        backupPackage: LocalBackupPackage,
    ): Unit = withContext(Dispatchers.IO) {
        require(backupPackage.version == BACKUP_FORMAT_VERSION) {
            string(R.string.repository_vault_backup_version_unsupported, backupPackage.version)
        }

        val normalizedEncryptedVaultJson = encryptedVaultToJson(
            EncryptedVaultJsonCodec.decode(backupPackage.encryptedVaultJson),
        ).toString()

        val previousSecuritySnapshot = if (masterPasswordManager.isMasterPasswordConfigured()) {
            masterPasswordManager.exportBackupSnapshot()
        } else {
            null
        }
        val previousEncryptedVaultJson = prefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON,
            null,
        )
        val previousLegacyTokensJson = prefs.getString(SteamVaultPreferenceKeys.KEY_TOKENS_JSON, null)
        val previousCloudBackupProfileJson = cloudBackupPrefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
            null,
        )
        val previousSteamSessionProfileJson = steamSessionPrefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
            null,
        )
        val previousSteamGuardDataProfileJson = steamProtocolLoginPrefs.getString(
            SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
            null,
        )
        val previousCloudBackupStatusJson = cloudBackupPrefs.getString(
            SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON,
            null,
        )
        val previousBackgroundCloudBackupConfigurationJson = cloudBackupPrefs.getString(
            SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON,
            null,
        )
        val previousAppSecuritySettings = readAppSecuritySettings()

        try {
            autoCloudBackupScheduler?.cancelPendingUploadsForManualRestore()
            masterPasswordManager.restoreBackupSnapshot(backupPackage.securitySnapshot)

            val restored = prefs.edit()
                .putString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON,
                    normalizedEncryptedVaultJson,
                )
                .remove(SteamVaultPreferenceKeys.KEY_TOKENS_JSON)
                .commit()

            if (!restored) {
                throw IllegalStateException(string(R.string.repository_vault_restore_save_failed))
            }
            restoreEncryptedProfileSnapshot(
                prefs = steamSessionPrefs,
                key = SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
                encryptedProfileJson = normalizeEncryptedSnapshot(
                    backupPackage.steamSessionProfileSnapshot?.encryptedProfileJson,
                ),
                failureMessage = string(R.string.repository_steam_session_save_failed),
            )
            restoreEncryptedProfileSnapshot(
                prefs = steamProtocolLoginPrefs,
                key = SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
                encryptedProfileJson = normalizeEncryptedSnapshot(
                    backupPackage.steamGuardDataProfileSnapshot?.encryptedProfileJson,
                ),
                failureMessage = string(R.string.repository_steam_session_save_failed),
            )
            backupPackage.cloudBackupProfileSnapshot?.let(::restoreCloudBackupProfileSnapshot)
            backupPackage.appSecuritySettings?.let(::writeAppSecuritySettings)
        } catch (error: Exception) {
            restorePreviousLocalState(
                previousSecuritySnapshot = previousSecuritySnapshot,
                previousEncryptedVaultJson = previousEncryptedVaultJson,
                previousLegacyTokensJson = previousLegacyTokensJson,
                previousCloudBackupProfileJson = previousCloudBackupProfileJson,
                previousSteamSessionProfileJson = previousSteamSessionProfileJson,
                previousSteamGuardDataProfileJson = previousSteamGuardDataProfileJson,
                previousCloudBackupStatusJson = previousCloudBackupStatusJson,
                previousBackgroundCloudBackupConfigurationJson = previousBackgroundCloudBackupConfigurationJson,
                previousAppSecuritySettings = previousAppSecuritySettings,
            )
            throw error
        }
    }

    private suspend fun readTokenArray(): JSONArray {
        migrateLegacyPlainVaultIfNeeded()

        val encryptedJson = prefs.getString(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON, null)
            ?: return JSONArray()
        val encryptedVault = EncryptedVaultJsonCodec.decode(encryptedJson)

        return withActiveVaultKey { vaultKey ->
            val cleartext = vaultCryptography.decryptVault(encryptedVault, vaultKey)
            try {
                JSONArray(cleartext.toString(StandardCharsets.UTF_8))
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun buildBackupMetadataSummary(
        normalizedSteamSessionProfileJson: String?,
        normalizedSteamGuardDataProfileJson: String?,
    ): LocalBackupMetadataSummary {
        val tokens = readActiveTokens()
            .sortedBy { token -> token.accountName.lowercase(Locale.US) }
        val sessions = readSteamSessionRecords(normalizedSteamSessionProfileJson)
        val guardDataRecords = readSteamGuardDataRecords(normalizedSteamGuardDataProfileJson)
        val sessionsByTokenId = sessions.associateBy { session -> session.tokenId }

        val tokenSummaries = tokens.map { token ->
            val session = sessionsByTokenId[token.id]
            val normalizedCookies = session?.let(::normalizeSessionCookies).orEmpty()
            val derivedSteamId = session?.steamId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: deriveSteamIdFromCookies(normalizedCookies)
            val matchingGuardData = findBestGuardDataMatch(
                records = guardDataRecords,
                accountName = token.accountName,
                steamId = derivedSteamId,
            )
            val hasSessionId = session?.sessionId?.isNotBlank() == true ||
                normalizedCookies.hasCookie("sessionid")
            val hasLoginCookie = normalizedCookies.hasCookie("steamLoginSecure") ||
                normalizedCookies.hasCookie("steamLogin")
            val hasIdentitySecret = !token.identitySecret.isNullOrBlank()
            val hasDeviceId = !token.deviceId.isNullOrBlank()
            val hasGuardData = !session?.guardData.isNullOrBlank() || matchingGuardData != null

            LocalBackupTokenSummary(
                accountNameHint = maskAccountName(token.accountName),
                accountNameFingerprint = fingerprintValue(token.accountName),
                steamIdHint = derivedSteamId?.let(::maskSteamId),
                steamIdFingerprint = derivedSteamId?.let(::fingerprintValue),
                platform = token.platform,
                sessionPlatform = session?.platform?.name,
                hasIdentitySecret = hasIdentitySecret,
                hasDeviceId = hasDeviceId,
                hasRevocationCode = !token.revocationCode.isNullOrBlank(),
                hasUri = !token.uri.isNullOrBlank(),
                hasSession = session != null,
                hasSessionId = hasSessionId,
                hasLoginCookie = hasLoginCookie,
                hasRefreshToken = !session?.refreshToken.isNullOrBlank(),
                hasGuardData = hasGuardData,
                hasConfirmationMaterial = hasIdentitySecret && hasDeviceId && hasSessionId && hasLoginCookie,
            )
        }

        return LocalBackupMetadataSummary(
            tokenCount = tokens.size,
            steamSessionCount = sessions.size,
            steamGuardDataCount = guardDataRecords.size,
            tokensWithSessionCount = tokenSummaries.count { summary -> summary.hasSession },
            tokensWithGuardDataCount = tokenSummaries.count { summary -> summary.hasGuardData },
            confirmationMaterialTokenCount = tokenSummaries.count { summary ->
                summary.hasConfirmationMaterial
            },
            tokenSummaries = tokenSummaries,
        )
    }

    private suspend fun readActiveTokens(): List<TokenRecord> {
        val tokenArray = readTokenArray()
        return buildList {
            for (index in 0 until tokenArray.length()) {
                val token = jsonToToken(tokenArray.getJSONObject(index))
                if (token.deletedAt == null) {
                    add(token)
                }
            }
        }
    }

    private suspend fun readSteamSessionRecords(
        normalizedEncryptedProfileJson: String?,
    ): List<SteamSessionRecord> {
        val encryptedProfileJson = normalizedEncryptedProfileJson?.trim()?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        return withActiveVaultKey { vaultKey ->
            val encryptedProfile = EncryptedVaultJsonCodec.decode(encryptedProfileJson)
            val cleartext = vaultCryptography.decryptVault(encryptedProfile, vaultKey)
            try {
                SteamSessionProfileCodec.decode(cleartext.toString(StandardCharsets.UTF_8)).sessions
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun readSteamGuardDataRecords(
        normalizedEncryptedProfileJson: String?,
    ): List<SteamGuardDataRecord> {
        val encryptedProfileJson = normalizedEncryptedProfileJson?.trim()?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        return withActiveVaultKey { vaultKey ->
            val encryptedProfile = EncryptedVaultJsonCodec.decode(encryptedProfileJson)
            val cleartext = vaultCryptography.decryptVault(encryptedProfile, vaultKey)
            try {
                SteamGuardDataProfileCodec.decode(cleartext.toString(StandardCharsets.UTF_8)).records
            } finally {
                cleartext.fill(0)
            }
        }
    }

    private suspend fun writeTokenArray(array: JSONArray) {
        writeEncryptedVault(array.toString())
    }

    private suspend fun writeEncryptedVault(cleartextJson: String) {
        withActiveVaultKey { vaultKey ->
            val cleartextBytes = cleartextJson.toByteArray(StandardCharsets.UTF_8)
            try {
                val encryptedVault = vaultCryptography.encryptVault(cleartextBytes, vaultKey)
                prefs.edit()
                    .putString(
                        SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON,
                        EncryptedVaultJsonCodec.encode(encryptedVault),
                    )
                    .remove(SteamVaultPreferenceKeys.KEY_TOKENS_JSON)
                    .commit()
            } finally {
                cleartextBytes.fill(0)
            }
        }
    }

    private suspend fun migrateLegacyPlainVaultIfNeeded() {
        val legacyJson = prefs.getString(SteamVaultPreferenceKeys.KEY_TOKENS_JSON, null) ?: return
        if (prefs.contains(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON)) {
            prefs.edit().remove(SteamVaultPreferenceKeys.KEY_TOKENS_JSON).commit()
            return
        }

        writeEncryptedVault(legacyJson)
    }

    private suspend fun <T> withActiveVaultKey(block: suspend (ByteArray) -> T): T {
        val vaultKey = masterPasswordManager.getActiveVaultKeyMaterial()
            ?: throw IllegalStateException(string(R.string.repository_vault_unlock_required))
        return try {
            block(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }

    private fun encryptedVaultToJson(encryptedVault: EncryptedVault): JSONObject {
        return JSONObject(EncryptedVaultJsonCodec.encode(encryptedVault))
    }

    private fun normalizeEncryptedSnapshot(encryptedJson: String?): String? {
        val normalized = encryptedJson?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return EncryptedVaultJsonCodec.encode(EncryptedVaultJsonCodec.decode(normalized))
    }

    private fun normalizeSessionCookies(session: SteamSessionRecord): List<SteamSessionCookie> {
        val cookies = session.cookies
            .filter { cookie -> cookie.name.isNotBlank() && cookie.value.isNotBlank() }
            .toMutableList()
        val sessionId = session.sessionId?.trim().orEmpty()
        if (sessionId.isNotBlank() && !cookies.hasCookie("sessionid")) {
            cookies += SteamSessionCookie(name = "sessionid", value = sessionId)
        }
        return cookies
    }

    private fun deriveSteamIdFromCookies(cookies: List<SteamSessionCookie>): String? {
        val rawCookieValue = cookies.firstOrNull { cookie ->
            cookie.name.equals("steamLoginSecure", ignoreCase = true) ||
                cookie.name.equals("steamLogin", ignoreCase = true)
        }?.value ?: return null
        val decoded = URLDecoder.decode(rawCookieValue, StandardCharsets.UTF_8.name())
        return decoded.substringBefore("||").takeIf { value ->
            value.isNotBlank() && value.all(Char::isDigit)
        }
    }

    private fun List<SteamSessionCookie>.hasCookie(name: String): Boolean {
        return any { cookie ->
            cookie.name.equals(name, ignoreCase = true) && cookie.value.isNotBlank()
        }
    }

    private fun findBestGuardDataMatch(
        records: List<SteamGuardDataRecord>,
        accountName: String?,
        steamId: String?,
    ): SteamGuardDataRecord? {
        val normalizedSteamId = steamId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAccountName = accountName?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            normalizedSteamId != null -> records.lastOrNull { record ->
                record.steamId == normalizedSteamId
            } ?: normalizedAccountName?.let { name ->
                records.lastOrNull { record ->
                    record.accountName?.trim()?.equals(name, ignoreCase = true) == true
                }
            }

            normalizedAccountName != null -> records.lastOrNull { record ->
                record.accountName?.trim()?.equals(normalizedAccountName, ignoreCase = true) == true
            }

            else -> null
        }
    }

    private fun maskAccountName(accountName: String): String {
        val normalized = accountName.trim()
        if (normalized.isEmpty()) {
            return ""
        }
        return when {
            normalized.length == 1 -> "*"
            normalized.length == 2 -> "${normalized.first()}*"
            normalized.length <= 4 -> "${normalized.first()}***${normalized.last()}"
            else -> "${normalized.take(2)}***${normalized.takeLast(2)}"
        }
    }

    private fun maskSteamId(steamId: String): String {
        val normalized = steamId.trim()
        if (normalized.length <= 4) {
            return normalized
        }
        return "${normalized.take(2)}***${normalized.takeLast(4)}"
    }

    private fun fingerprintValue(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.trim().lowercase(Locale.US).toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02x".format(byte.toInt() and 0xff))
            }
        }.take(16)
    }

    private fun jsonToToken(json: JSONObject): TokenRecord {
        return TokenRecord(
            id = json.getString("id"),
            platform = json.optString("platform", "steam"),
            accountName = json.getString("account_name"),
            sharedSecret = json.getString("shared_secret"),
            identitySecret = json.optString("identity_secret").takeIf { it.isNotBlank() },
            serialNumber = json.optString("serial_number").takeIf { it.isNotBlank() },
            revocationCode = json.optString("revocation_code").takeIf { it.isNotBlank() },
            secret1 = json.optString("secret_1").takeIf { it.isNotBlank() },
            deviceId = json.optString("device_id").takeIf { it.isNotBlank() },
            tokenGid = json.optString("token_gid").takeIf { it.isNotBlank() },
            uri = json.optString("uri").takeIf { it.isNotBlank() },
            createdAt = json.getString("created_at"),
            updatedAt = json.getString("updated_at"),
            deletedAt = json.optString("deleted_at").takeIf { it.isNotBlank() },
        )
    }

    private fun tokenToJson(token: TokenRecord): JSONObject {
        return JSONObject()
            .put("id", token.id)
            .put("platform", token.platform)
            .put("account_name", token.accountName)
            .put("shared_secret", token.sharedSecret)
            .put("identity_secret", token.identitySecret ?: "")
            .put("serial_number", token.serialNumber ?: "")
            .put("revocation_code", token.revocationCode ?: "")
            .put("secret_1", token.secret1 ?: "")
            .put("device_id", token.deviceId ?: "")
            .put("token_gid", token.tokenGid ?: "")
            .put("uri", token.uri ?: "")
            .put("created_at", token.createdAt)
            .put("updated_at", token.updatedAt)
            .put("deleted_at", token.deletedAt ?: "")
    }

    private fun resolveDeviceId(): String {
        return Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown-device"
    }

    private fun nowIsoUtc(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private fun readAppSecuritySettings(): AppSecuritySettings {
        return AppSecuritySettings(
            secureScreensEnabled = securityPrefs.getBoolean(
                SteamVaultPreferenceKeys.KEY_SECURE_SCREENS_ENABLED,
                true,
            ),
            biometricQuickUnlockEnabled = securityPrefs.getBoolean(
                SteamVaultPreferenceKeys.KEY_BIOMETRIC_QUICK_UNLOCK_ENABLED,
                false,
            ),
            autoLockTimeout = AutoLockTimeoutOption.fromPreferenceValue(
                securityPrefs.getString(
                    SteamVaultPreferenceKeys.KEY_AUTO_LOCK_TIMEOUT,
                    AutoLockTimeoutOption.default.preferenceValue,
                ),
            ),
        )
    }

    private fun writeAppSecuritySettings(settings: AppSecuritySettings) {
        val saved = securityPrefs.edit()
            .putBoolean(
                SteamVaultPreferenceKeys.KEY_SECURE_SCREENS_ENABLED,
                settings.secureScreensEnabled,
            )
            .putBoolean(
                SteamVaultPreferenceKeys.KEY_BIOMETRIC_QUICK_UNLOCK_ENABLED,
                settings.biometricQuickUnlockEnabled,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_AUTO_LOCK_TIMEOUT,
                settings.autoLockTimeout.preferenceValue,
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(string(R.string.repository_security_settings_save_failed))
        }
    }

    private fun restoreCloudBackupProfileSnapshot(snapshot: CloudBackupProfileSnapshot) {
        val saved = cloudBackupPrefs.edit().apply {
            if (snapshot.encryptedProfileJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
                    snapshot.encryptedProfileJson,
                )
            }
            remove(SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON)
            remove(SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON)
        }.commit()

        if (!saved) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_save_failed))
        }
    }

    private fun restoreEncryptedProfileSnapshot(
        prefs: android.content.SharedPreferences,
        key: String,
        encryptedProfileJson: String?,
        failureMessage: String,
    ) {
        val saved = prefs.edit().apply {
            if (encryptedProfileJson == null) {
                remove(key)
            } else {
                putString(key, encryptedProfileJson)
            }
        }.commit()

        if (!saved) {
            throw IllegalStateException(failureMessage)
        }
    }

    private suspend fun restorePreviousLocalState(
        previousSecuritySnapshot: com.example.steam_vault_app.domain.security.MasterPasswordBackupSnapshot?,
        previousEncryptedVaultJson: String?,
        previousLegacyTokensJson: String?,
        previousCloudBackupProfileJson: String?,
        previousSteamSessionProfileJson: String?,
        previousSteamGuardDataProfileJson: String?,
        previousCloudBackupStatusJson: String?,
        previousBackgroundCloudBackupConfigurationJson: String?,
        previousAppSecuritySettings: AppSecuritySettings,
    ) {
        if (previousSecuritySnapshot == null) {
            clearStoredSecuritySnapshot()
        } else {
            masterPasswordManager.restoreBackupSnapshot(previousSecuritySnapshot)
        }

        val editor = prefs.edit()
        if (previousEncryptedVaultJson == null) {
            editor.remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON)
        } else {
            editor.putString(
                SteamVaultPreferenceKeys.KEY_ENCRYPTED_VAULT_JSON,
                previousEncryptedVaultJson,
            )
        }

        if (previousLegacyTokensJson == null) {
            editor.remove(SteamVaultPreferenceKeys.KEY_TOKENS_JSON)
        } else {
            editor.putString(
                SteamVaultPreferenceKeys.KEY_TOKENS_JSON,
                previousLegacyTokensJson,
            )
        }

        editor.commit()

        steamSessionPrefs.edit().apply {
            if (previousSteamSessionProfileJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON,
                    previousSteamSessionProfileJson,
                )
            }
        }.commit()

        steamProtocolLoginPrefs.edit().apply {
            if (previousSteamGuardDataProfileJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON,
                    previousSteamGuardDataProfileJson,
                )
            }
        }.commit()

        val cloudBackupRestored = cloudBackupPrefs.edit().apply {
            if (previousCloudBackupProfileJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON,
                    previousCloudBackupProfileJson,
                )
            }

            if (previousCloudBackupStatusJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_CLOUD_BACKUP_STATUS_JSON,
                    previousCloudBackupStatusJson,
                )
            }

            if (previousBackgroundCloudBackupConfigurationJson == null) {
                remove(SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON)
            } else {
                putString(
                    SteamVaultPreferenceKeys.KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON,
                    previousBackgroundCloudBackupConfigurationJson,
                )
            }
        }.commit()

        if (!cloudBackupRestored) {
            throw IllegalStateException(string(R.string.repository_cloud_backup_rollback_failed))
        }

        writeAppSecuritySettings(previousAppSecuritySettings)
    }

    private suspend fun clearStoredSecuritySnapshot() {
        masterPasswordManager.clearUnlockedSession()
        masterPasswordManager.clearBiometricQuickUnlock()
        val cleared = securityPrefs.edit()
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM)
            .remove(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION)
            .remove(SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT)
            .remove(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON)
            .commit()

        if (!cleared) {
            throw IllegalStateException(string(R.string.repository_security_snapshot_clear_failed))
        }
    }

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return appContext.getString(resId, *formatArgs)
    }

    companion object {
        private const val BACKUP_FORMAT_VERSION = 1
    }
}
