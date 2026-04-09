package com.example.steam_vault_app.domain.security

import javax.crypto.Cipher

interface MasterPasswordManager {
    suspend fun isMasterPasswordConfigured(): Boolean

    suspend fun createMasterPassword(rawPassword: CharArray)

    suspend fun changeMasterPassword(rawPassword: CharArray)

    suspend fun unlock(rawPassword: CharArray): Boolean

    suspend fun getActiveVaultKeyMaterial(): ByteArray?

    suspend fun hasBiometricQuickUnlock(): Boolean

    fun prepareBiometricEnrollmentCipher(): Cipher

    suspend fun enableBiometricQuickUnlock(cipher: Cipher)

    fun prepareBiometricUnlockCipher(): Cipher?

    suspend fun unlockWithBiometricCipher(cipher: Cipher): Boolean

    suspend fun clearBiometricQuickUnlock()

    suspend fun exportBackupSnapshot(): MasterPasswordBackupSnapshot

    suspend fun restoreBackupSnapshot(snapshot: MasterPasswordBackupSnapshot)

    suspend fun clearUnlockedSession()
}
