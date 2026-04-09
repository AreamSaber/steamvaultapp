package com.example.steam_vault_app.domain.model

data class SyncSnapshot(
    val deviceId: String,
    val lastSyncedAt: String? = null,
    val pendingChanges: Int = 0,
    val softDeletedRecords: Int = 0,
    val lastFailureMessage: String? = null,
)
