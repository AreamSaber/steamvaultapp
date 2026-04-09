package com.example.steam_vault_app.data.security

import android.content.Context
import android.util.Base64
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.local.SteamVaultPreferenceKeys
import com.example.steam_vault_app.domain.security.MasterPasswordBackupSnapshot
import com.example.steam_vault_app.domain.security.MasterPasswordManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMasterPasswordManager(
    context: Context,
) : MasterPasswordManager {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SteamVaultPreferenceKeys.SECURITY_PREFS,
        Context.MODE_PRIVATE,
    )
    private val secureRandom = SecureRandom()
    private val keystoreVaultKeyProtector = AndroidKeystoreVaultKeyProtector()
    private val biometricVaultKeyProtector = AndroidBiometricVaultKeyProtector()
    private val argon2Kt by lazy(LazyThreadSafetyMode.NONE) { Argon2Kt() }

    @Volatile
    private var activeVaultKeyMaterial: ByteArray? = null

    override suspend fun isMasterPasswordConfigured(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT) &&
            prefs.contains(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH) &&
            prefs.contains(SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT)
    }

    override suspend fun createMasterPassword(rawPassword: CharArray) = withContext(Dispatchers.IO) {
        require(rawPassword.isNotEmpty()) { string(R.string.master_password_blank) }
        require(rawPassword.size >= MIN_PASSWORD_LENGTH) { string(R.string.master_password_too_short) }

        val currentKdfConfig = currentPasswordKdfConfig()
        val passwordSalt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val derivedHash = derivePasswordHash(rawPassword, passwordSalt, currentKdfConfig)
        val vaultKeyMaterial = ByteArray(VAULT_KEY_BYTES).also(secureRandom::nextBytes)
        var keepSessionUnlocked = false

        try {
            val wrappedVaultKey = buildWrappedVaultKeyRecord(
                rawPassword = rawPassword,
                kdfConfig = currentKdfConfig,
                vaultKeyMaterial = vaultKeyMaterial,
            )

            val saved = prefs.edit()
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT,
                    passwordSalt.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH,
                    derivedHash.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME,
                    currentKdfConfig.name,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                    currentKdfConfig.iterations,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB,
                    currentKdfConfig.memoryKiB,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM,
                    currentKdfConfig.parallelism,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
                    PASSWORD_FORMAT_VERSION,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT,
                    wrappedVaultKey.wrappingSaltBase64,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                    wrappedVaultKey.envelopeJson,
                )
                .commit()

            if (!saved) {
                throw IllegalStateException(string(R.string.create_password_save_local_failed))
            }

            replaceActiveVaultKey(vaultKeyMaterial)
            keepSessionUnlocked = true
        } finally {
            passwordSalt.fill(0)
            derivedHash.fill(0)
            vaultKeyMaterial.fill(0)
            if (!keepSessionUnlocked) {
                replaceActiveVaultKey(null)
            }
        }

        Unit
    }

    override suspend fun changeMasterPassword(rawPassword: CharArray) = withContext(Dispatchers.IO) {
        require(rawPassword.isNotEmpty()) { string(R.string.master_password_blank) }
        require(rawPassword.size >= MIN_PASSWORD_LENGTH) { string(R.string.master_password_too_short) }

        val activeVaultKey = activeVaultKeyMaterial?.copyOf()
            ?: throw IllegalStateException(string(R.string.master_password_change_requires_unlock))
        val targetConfig = currentPasswordKdfConfig()
        val passwordSalt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val verifierHash = derivePasswordHash(rawPassword, passwordSalt, targetConfig)

        try {
            val wrappedVaultKey = buildWrappedVaultKeyRecord(
                rawPassword = rawPassword,
                kdfConfig = targetConfig,
                vaultKeyMaterial = activeVaultKey,
            )

            val saved = prefs.edit()
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT,
                    passwordSalt.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH,
                    verifierHash.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME,
                    targetConfig.name,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                    targetConfig.iterations,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB,
                    targetConfig.memoryKiB,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM,
                    targetConfig.parallelism,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
                    PASSWORD_FORMAT_VERSION,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT,
                    wrappedVaultKey.wrappingSaltBase64,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                    wrappedVaultKey.envelopeJson,
                )
                .commit()

            if (!saved) {
                throw IllegalStateException(string(R.string.master_password_change_save_failed))
            }

            runCatching { biometricVaultKeyProtector.deleteSecretKey() }
        } finally {
            activeVaultKey.fill(0)
            passwordSalt.fill(0)
            verifierHash.fill(0)
        }

        Unit
    }

    override suspend fun unlock(rawPassword: CharArray): Boolean = withContext(Dispatchers.IO) {
        val passwordSalt = prefs.getString(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT, null)
            ?.fromBase64()
            ?: return@withContext false
        val vaultSalt = prefs.getString(SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT, null)
            ?.fromBase64()
            ?: return@withContext false
        val storedHash = prefs.getString(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH, null)
            ?.fromBase64()
            ?: return@withContext false
        val storedKdfConfig = readStoredPasswordKdfConfig()

        val candidateHash = derivePasswordHash(rawPassword, passwordSalt, storedKdfConfig)
        val matches = try {
            MessageDigest.isEqual(storedHash, candidateHash)
        } finally {
            passwordSalt.fill(0)
            storedHash.fill(0)
            candidateHash.fill(0)
        }

        if (!matches) {
            vaultSalt.fill(0)
            replaceActiveVaultKey(null)
            return@withContext false
        }

        val unlockedVaultKey = try {
            if (prefs.contains(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON)) {
                unlockWrappedVaultKey(
                    rawPassword = rawPassword,
                    kdfConfig = storedKdfConfig,
                    wrappingSalt = vaultSalt,
                )
            } else {
                unlockLegacyVaultKey(
                    rawPassword = rawPassword,
                    kdfConfig = storedKdfConfig,
                    legacyVaultSalt = vaultSalt,
                )
            }
        } finally {
            vaultSalt.fill(0)
        }

        try {
            migratePasswordKdfIfNeeded(
                rawPassword = rawPassword,
                currentConfig = storedKdfConfig,
                unlockedVaultKey = unlockedVaultKey,
            )
            replaceActiveVaultKey(unlockedVaultKey)
            true
        } finally {
            unlockedVaultKey.fill(0)
        }
    }

    override suspend fun getActiveVaultKeyMaterial(): ByteArray? = withContext(Dispatchers.Default) {
        activeVaultKeyMaterial?.copyOf()
    }

    override suspend fun hasBiometricQuickUnlock(): Boolean = withContext(Dispatchers.IO) {
        val wrappedVaultKeyJson = prefs.getString(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON, null)
            ?: return@withContext false
        WrappedVaultKeyEnvelopeCodec.decode(wrappedVaultKeyJson).biometricWrappedKey != null
    }

    override fun prepareBiometricEnrollmentCipher(): Cipher {
        return biometricVaultKeyProtector.prepareEncryptionCipher()
    }

    override suspend fun enableBiometricQuickUnlock(cipher: Cipher) = withContext(Dispatchers.IO) {
        val activeVaultKey = activeVaultKeyMaterial?.copyOf()
            ?: throw IllegalStateException(string(R.string.master_password_biometric_requires_unlock))
        try {
            val biometricWrappedKey = biometricVaultKeyProtector.encryptWithCipher(
                vaultKeyMaterial = activeVaultKey,
                cipher = cipher,
            )
            persistWrappedVaultKeyEnvelope(
                readWrappedVaultKeyEnvelope().copy(
                    version = WRAPPED_VAULT_KEY_VERSION_BIOMETRIC,
                    biometricWrappedKey = biometricWrappedKey,
                ),
            )
        } finally {
            activeVaultKey.fill(0)
        }
    }

    override fun prepareBiometricUnlockCipher(): Cipher? {
        val payload = prefs.getString(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON, null)
            ?.let(WrappedVaultKeyEnvelopeCodec::decode)
            ?.biometricWrappedKey
            ?: return null

        return biometricVaultKeyProtector.prepareDecryptionCipher(payload)
    }

    override suspend fun unlockWithBiometricCipher(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val biometricPayload = readWrappedVaultKeyEnvelope().biometricWrappedKey ?: return@withContext false
        val unlockedVaultKey = biometricVaultKeyProtector.decryptWithCipher(
            payload = biometricPayload,
            cipher = cipher,
        )
        try {
            require(unlockedVaultKey.size == VAULT_KEY_BYTES) {
                string(R.string.master_password_biometric_key_length_invalid)
            }
            replaceActiveVaultKey(unlockedVaultKey)
            true
        } finally {
            unlockedVaultKey.fill(0)
        }
    }

    override suspend fun clearBiometricQuickUnlock() = withContext(Dispatchers.IO) {
        if (!prefs.contains(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON)) {
            runCatching { biometricVaultKeyProtector.deleteSecretKey() }
            return@withContext Unit
        }
        persistWrappedVaultKeyEnvelope(
            readWrappedVaultKeyEnvelope().copy(
                biometricWrappedKey = null,
            ),
        )
        runCatching { biometricVaultKeyProtector.deleteSecretKey() }
        Unit
    }

    override suspend fun exportBackupSnapshot(): MasterPasswordBackupSnapshot = withContext(Dispatchers.IO) {
        val masterPasswordSalt = prefs.getString(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT, null)
            ?: throw IllegalStateException(string(R.string.master_password_salt_missing_internal))
        val masterPasswordHash = prefs.getString(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH, null)
            ?: throw IllegalStateException(string(R.string.master_password_hash_missing_internal))
        val wrappedVaultKeyJson = WrappedVaultKeyEnvelopeCodec.encode(
            readWrappedVaultKeyEnvelope().copy(
                biometricWrappedKey = null,
            ),
        )
        val vaultKeySalt = prefs.getString(SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT, null)
            ?: throw IllegalStateException(string(R.string.master_password_vault_key_salt_missing))
        val kdfConfig = readStoredPasswordKdfConfig()

        MasterPasswordBackupSnapshot(
            masterPasswordSaltBase64 = masterPasswordSalt,
            masterPasswordHashBase64 = masterPasswordHash,
            masterPasswordKdfName = kdfConfig.name,
            masterPasswordIterations = kdfConfig.iterations,
            masterPasswordMemoryKiB = kdfConfig.memoryKiB,
            masterPasswordParallelism = kdfConfig.parallelism,
            masterPasswordVersion = prefs.getInt(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
                PASSWORD_FORMAT_VERSION,
            ),
            vaultKeySaltBase64 = vaultKeySalt,
            wrappedVaultKeyJson = wrappedVaultKeyJson,
        )
    }

    override suspend fun restoreBackupSnapshot(
        snapshot: MasterPasswordBackupSnapshot,
    ) = withContext(Dispatchers.IO) {
        require(snapshot.masterPasswordSaltBase64.isNotBlank()) { string(R.string.master_password_backup_salt_missing) }
        require(snapshot.masterPasswordHashBase64.isNotBlank()) { string(R.string.master_password_backup_hash_missing) }
        require(snapshot.vaultKeySaltBase64.isNotBlank()) { string(R.string.master_password_backup_vault_key_salt_missing) }
        require(snapshot.wrappedVaultKeyJson.isNotBlank()) { string(R.string.master_password_backup_wrapped_key_missing) }

        val normalizedKdfConfig = normalizeRestoredKdfConfig(snapshot)
        val saved = prefs.edit()
            .putString(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT,
                snapshot.masterPasswordSaltBase64,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH,
                snapshot.masterPasswordHashBase64,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME,
                normalizedKdfConfig.name,
            )
            .putInt(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                normalizedKdfConfig.iterations,
            )
            .putInt(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB,
                normalizedKdfConfig.memoryKiB,
            )
            .putInt(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM,
                normalizedKdfConfig.parallelism,
            )
            .putInt(
                SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
                snapshot.masterPasswordVersion,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT,
                snapshot.vaultKeySaltBase64,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                snapshot.wrappedVaultKeyJson,
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(string(R.string.master_password_restore_snapshot_save_failed))
        }

        runCatching { biometricVaultKeyProtector.deleteSecretKey() }
        replaceActiveVaultKey(null)
    }

    override suspend fun clearUnlockedSession() = withContext(Dispatchers.Default) {
        replaceActiveVaultKey(null)
    }

    private fun derivePasswordHash(
        rawPassword: CharArray,
        salt: ByteArray,
        kdfConfig: PasswordKdfConfig,
    ): ByteArray {
        return when (kdfConfig.name) {
            PASSWORD_KDF_ARGON2ID -> deriveArgon2idHash(rawPassword, salt, kdfConfig)
            PASSWORD_KDF_PBKDF2 -> derivePbkdf2Hash(rawPassword, salt, kdfConfig.iterations)
            else -> throw IllegalStateException(
                string(R.string.master_password_unsupported_kdf, kdfConfig.name),
            )
        }
    }

    private fun deriveArgon2idHash(
        rawPassword: CharArray,
        salt: ByteArray,
        kdfConfig: PasswordKdfConfig,
    ): ByteArray {
        val passwordBytes = rawPassword.toUtf8Bytes()
        return try {
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = salt,
                tCostInIterations = kdfConfig.iterations,
                mCostInKibibyte = kdfConfig.memoryKiB,
                parallelism = kdfConfig.parallelism,
                hashLengthInBytes = HASH_BYTES,
            )
            val rawHash = result.rawHashAsByteArray()
            val encodedOutput = result.encodedOutputAsByteArray()
            encodedOutput.fill(0)
            rawHash
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun derivePbkdf2Hash(
        rawPassword: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(rawPassword, salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun replaceActiveVaultKey(nextValue: ByteArray?) {
        activeVaultKeyMaterial?.fill(0)
        activeVaultKeyMaterial = nextValue?.copyOf()
    }

    private fun unlockWrappedVaultKey(
        rawPassword: CharArray,
        kdfConfig: PasswordKdfConfig,
        wrappingSalt: ByteArray,
    ): ByteArray {
        val wrappedVaultKeyJson = prefs.getString(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON, null)
            ?: throw IllegalStateException(string(R.string.master_password_wrapped_key_metadata_missing))
        val envelope = WrappedVaultKeyEnvelopeCodec.decode(wrappedVaultKeyJson)
        val wrappingKey = derivePasswordHash(rawPassword, wrappingSalt, kdfConfig)

        val passwordUnwrappedKey = try {
            decryptWrappedVaultKey(envelope.passwordWrappedKey, wrappingKey)
        } finally {
            wrappingKey.fill(0)
        }

        val keystoreHealthy = validateKeystoreWrappedKey(
            passwordUnwrappedKey = passwordUnwrappedKey,
            keystoreWrappedKey = envelope.keystoreWrappedKey,
        )

        if (!keystoreHealthy) {
            val refreshedRecord = buildWrappedVaultKeyRecord(
                rawPassword = rawPassword,
                kdfConfig = kdfConfig,
                vaultKeyMaterial = passwordUnwrappedKey,
                wrappingSalt = wrappingSalt,
            )
            persistWrappedVaultKeyRecord(refreshedRecord)
        }

        return passwordUnwrappedKey
    }

    private fun unlockLegacyVaultKey(
        rawPassword: CharArray,
        kdfConfig: PasswordKdfConfig,
        legacyVaultSalt: ByteArray,
    ): ByteArray {
        val legacyVaultKey = derivePasswordHash(rawPassword, legacyVaultSalt, kdfConfig)
        try {
            val migratedRecord = buildWrappedVaultKeyRecord(
                rawPassword = rawPassword,
                kdfConfig = kdfConfig,
                vaultKeyMaterial = legacyVaultKey,
            )
            persistWrappedVaultKeyRecord(migratedRecord)
            return legacyVaultKey.copyOf()
        } finally {
            legacyVaultKey.fill(0)
        }
    }

    private fun buildWrappedVaultKeyRecord(
        rawPassword: CharArray,
        kdfConfig: PasswordKdfConfig,
        vaultKeyMaterial: ByteArray,
        wrappingSalt: ByteArray? = null,
    ): PersistedWrappedVaultKeyRecord {
        val effectiveWrappingSalt = wrappingSalt?.copyOf() ?: ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val wrappingKey = derivePasswordHash(rawPassword, effectiveWrappingSalt, kdfConfig)
        return try {
            val passwordWrappedKey = encryptWrappedVaultKey(vaultKeyMaterial, wrappingKey)
            val keystoreWrappedKey = runCatching {
                keystoreVaultKeyProtector.encrypt(vaultKeyMaterial)
            }.getOrNull()

            PersistedWrappedVaultKeyRecord(
                wrappingSaltBase64 = effectiveWrappingSalt.toBase64(),
                envelopeJson = WrappedVaultKeyEnvelopeCodec.encode(
                    WrappedVaultKeyEnvelope(
                        version = WRAPPED_VAULT_KEY_VERSION,
                        passwordWrappedKey = passwordWrappedKey,
                        keystoreWrappedKey = keystoreWrappedKey,
                        biometricWrappedKey = null,
                    ),
                ),
            )
        } finally {
            effectiveWrappingSalt.fill(0)
            wrappingKey.fill(0)
        }
    }

    private fun migratePasswordKdfIfNeeded(
        rawPassword: CharArray,
        currentConfig: PasswordKdfConfig,
        unlockedVaultKey: ByteArray,
    ) {
        val targetConfig = currentPasswordKdfConfig()
        val currentVersion = prefs.getInt(
            SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
            LEGACY_PASSWORD_FORMAT_VERSION,
        )

        if (currentVersion >= PASSWORD_FORMAT_VERSION && currentConfig == targetConfig) {
            return
        }

        val passwordSalt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val verifierHash = derivePasswordHash(rawPassword, passwordSalt, targetConfig)

        try {
            val wrappedVaultKey = buildWrappedVaultKeyRecord(
                rawPassword = rawPassword,
                kdfConfig = targetConfig,
                vaultKeyMaterial = unlockedVaultKey,
            )

            val migrated = prefs.edit()
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_SALT,
                    passwordSalt.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_HASH,
                    verifierHash.toBase64(),
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME,
                    targetConfig.name,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                    targetConfig.iterations,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB,
                    targetConfig.memoryKiB,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM,
                    targetConfig.parallelism,
                )
                .putInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
                    PASSWORD_FORMAT_VERSION,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT,
                    wrappedVaultKey.wrappingSaltBase64,
                )
                .putString(
                    SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                    wrappedVaultKey.envelopeJson,
                )
                .commit()

            if (!migrated) {
                throw IllegalStateException(string(R.string.master_password_migrate_settings_failed))
            }
        } finally {
            passwordSalt.fill(0)
            verifierHash.fill(0)
        }
    }

    private fun persistWrappedVaultKeyRecord(record: PersistedWrappedVaultKeyRecord) {
        prefs.edit()
            .putString(
                SteamVaultPreferenceKeys.KEY_VAULT_KEY_SALT,
                record.wrappingSaltBase64,
            )
            .putString(
                SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                record.envelopeJson,
            )
            .commit()
    }

    private fun persistWrappedVaultKeyEnvelope(envelope: WrappedVaultKeyEnvelope) {
        val saved = prefs.edit()
            .putString(
                SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON,
                WrappedVaultKeyEnvelopeCodec.encode(envelope),
            )
            .commit()

        if (!saved) {
            throw IllegalStateException(string(R.string.master_password_biometric_config_save_failed))
        }
    }

    private fun readWrappedVaultKeyEnvelope(): WrappedVaultKeyEnvelope {
        val wrappedVaultKeyJson = prefs.getString(SteamVaultPreferenceKeys.KEY_WRAPPED_VAULT_KEY_JSON, null)
            ?: throw IllegalStateException(string(R.string.master_password_wrapped_key_metadata_missing))
        return WrappedVaultKeyEnvelopeCodec.decode(wrappedVaultKeyJson)
    }

    private fun validateKeystoreWrappedKey(
        passwordUnwrappedKey: ByteArray,
        keystoreWrappedKey: KeystoreEncryptedPayload?,
    ): Boolean {
        if (keystoreWrappedKey == null) {
            return false
        }

        val keystoreKey = runCatching {
            keystoreVaultKeyProtector.decrypt(keystoreWrappedKey)
        }.getOrNull() ?: return false

        return try {
            MessageDigest.isEqual(passwordUnwrappedKey, keystoreKey)
        } finally {
            keystoreKey.fill(0)
        }
    }

    private fun encryptWrappedVaultKey(
        vaultKeyMaterial: ByteArray,
        wrappingKey: ByteArray,
    ): WrappedKeyCopy {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(wrappingKey, AES_KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(vaultKeyMaterial)

        return WrappedKeyCopy(
            cipherName = VAULT_KEY_CIPHER_NAME,
            nonceBase64 = nonce.toBase64(),
            ciphertextBase64 = ciphertext.toBase64(),
        )
    }

    private fun decryptWrappedVaultKey(
        wrappedKeyCopy: WrappedKeyCopy,
        wrappingKey: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(wrappingKey, AES_KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, wrappedKeyCopy.nonceBase64.fromBase64()),
        )
        return cipher.doFinal(wrappedKeyCopy.ciphertextBase64.fromBase64())
    }

    private fun currentPasswordKdfConfig(): PasswordKdfConfig {
        return PasswordKdfConfig(
            name = PASSWORD_KDF_ARGON2ID,
            iterations = ARGON2_ITERATIONS,
            memoryKiB = ARGON2_MEMORY_KIB,
            parallelism = ARGON2_PARALLELISM,
        )
    }

    private fun readStoredPasswordKdfConfig(): PasswordKdfConfig {
        val storedVersion = prefs.getInt(
            SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_VERSION,
            LEGACY_PASSWORD_FORMAT_VERSION,
        )
        val storedName = prefs.getString(SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_KDF_NAME, null)

        return if (storedName == PASSWORD_KDF_ARGON2ID || storedVersion >= PASSWORD_FORMAT_VERSION) {
            PasswordKdfConfig(
                name = PASSWORD_KDF_ARGON2ID,
                iterations = prefs.getInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                    ARGON2_ITERATIONS,
                ),
                memoryKiB = prefs.getInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_MEMORY_KIB,
                    ARGON2_MEMORY_KIB,
                ),
                parallelism = prefs.getInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_PARALLELISM,
                    ARGON2_PARALLELISM,
                ),
            )
        } else {
            PasswordKdfConfig(
                name = PASSWORD_KDF_PBKDF2,
                iterations = prefs.getInt(
                    SteamVaultPreferenceKeys.KEY_MASTER_PASSWORD_ITERATIONS,
                    PBKDF2_ITERATIONS,
                ),
                memoryKiB = 0,
                parallelism = 1,
            )
        }
    }

    private fun normalizeRestoredKdfConfig(
        snapshot: MasterPasswordBackupSnapshot,
    ): PasswordKdfConfig {
        return when (snapshot.masterPasswordKdfName) {
            PASSWORD_KDF_ARGON2ID -> PasswordKdfConfig(
                name = PASSWORD_KDF_ARGON2ID,
                iterations = snapshot.masterPasswordIterations.coerceAtLeast(ARGON2_ITERATIONS),
                memoryKiB = snapshot.masterPasswordMemoryKiB.takeIf { it > 0 } ?: ARGON2_MEMORY_KIB,
                parallelism = snapshot.masterPasswordParallelism.takeIf { it > 0 }
                    ?: ARGON2_PARALLELISM,
            )

            PASSWORD_KDF_PBKDF2, "" -> PasswordKdfConfig(
                name = PASSWORD_KDF_PBKDF2,
                iterations = snapshot.masterPasswordIterations,
                memoryKiB = 0,
                parallelism = 1,
            )

            else -> throw IllegalArgumentException(
                string(R.string.master_password_backup_unsupported_kdf, snapshot.masterPasswordKdfName),
            )
        }
    }

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return appContext.getString(resId, *formatArgs)
    }

    private fun CharArray.toUtf8Bytes(): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(this))
        return ByteArray(byteBuffer.remaining()).also(byteBuffer::get)
    }

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    private data class PersistedWrappedVaultKeyRecord(
        val wrappingSaltBase64: String,
        val envelopeJson: String,
    )

    private data class PasswordKdfConfig(
        val name: String,
        val iterations: Int,
        val memoryKiB: Int,
        val parallelism: Int,
    )

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val ARGON2_ITERATIONS = 3
        private const val ARGON2_MEMORY_KIB = 65_536
        private const val ARGON2_PARALLELISM = 2
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        private const val LEGACY_PASSWORD_FORMAT_VERSION = 2
        private const val PASSWORD_FORMAT_VERSION = 3
        private const val MIN_PASSWORD_LENGTH = 10
        private const val VAULT_KEY_BYTES = 32
        private const val WRAPPED_VAULT_KEY_VERSION = 1
        private const val WRAPPED_VAULT_KEY_VERSION_BIOMETRIC = 2
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_ALGORITHM = "AES"
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val VAULT_KEY_CIPHER_NAME = "aes-256-gcm"
        private const val PASSWORD_KDF_PBKDF2 = "pbkdf2"
        private const val PASSWORD_KDF_ARGON2ID = "argon2id"
    }
}
