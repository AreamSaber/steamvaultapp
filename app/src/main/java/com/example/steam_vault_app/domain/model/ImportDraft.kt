package com.example.steam_vault_app.domain.model

enum class ImportSource {
    JSON_PASTE,
    QR_SCAN,
    MANUAL_ENTRY,
    STEAM_BINDING,
    MAFILE,
}

data class ImportDraft(
    val source: ImportSource,
    val accountName: String,
    val sharedSecret: String,
    val identitySecret: String? = null,
    val serialNumber: String? = null,
    val revocationCode: String? = null,
    val secret1: String? = null,
    val deviceId: String? = null,
    val tokenGid: String? = null,
    val uri: String? = null,
    val rawPayload: String? = null,
    val importedSession: ImportedSteamSessionDraft? = null,
)
