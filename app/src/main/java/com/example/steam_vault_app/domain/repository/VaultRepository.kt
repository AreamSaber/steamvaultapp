package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.LocalBackupPackage
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.model.VaultBlob

interface VaultRepository {
    suspend fun initializeEmptyVault()

    suspend fun hasVault(): Boolean

    suspend fun getTokens(): List<TokenRecord>

    suspend fun getToken(tokenId: String): TokenRecord?

    suspend fun saveImportedToken(importDraft: ImportDraft): TokenRecord

    suspend fun deleteToken(tokenId: String)

    suspend fun exportVault(): VaultBlob

    suspend fun exportLocalBackup(): LocalBackupPackage

    suspend fun restoreLocalBackup(backupPackage: LocalBackupPackage)
}
