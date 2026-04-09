package com.example.steam_vault_app.domain.model

data class SteamGuardAccountSnapshot(
    val tokenId: String,
    val accountName: String,
    val authenticator: SteamAuthenticatorSecrets,
    val session: SteamSessionRecord? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
) {
    val steamId: String?
        get() = session?.steamId

    val hasProtocolSession: Boolean
        get() = !session?.accessToken.isNullOrBlank() && !session?.refreshToken.isNullOrBlank()

    val hasWebConfirmationSession: Boolean
        get() = !session?.sessionId.isNullOrBlank() && session?.cookies?.isNotEmpty() == true

    fun toTokenRecord(): TokenRecord {
        return TokenRecord(
            id = tokenId,
            accountName = accountName,
            sharedSecret = authenticator.sharedSecret,
            identitySecret = authenticator.identitySecret,
            serialNumber = authenticator.serialNumber,
            revocationCode = authenticator.revocationCode,
            secret1 = authenticator.secret1,
            deviceId = authenticator.deviceId,
            tokenGid = authenticator.tokenGid,
            uri = authenticator.uri,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }

    fun toImportDraft(
        source: ImportSource = ImportSource.STEAM_BINDING,
        rawPayload: String? = null,
    ): ImportDraft {
        return ImportDraft(
            source = source,
            accountName = accountName,
            sharedSecret = authenticator.sharedSecret,
            identitySecret = authenticator.identitySecret,
            serialNumber = authenticator.serialNumber,
            revocationCode = authenticator.revocationCode,
            secret1 = authenticator.secret1,
            deviceId = authenticator.deviceId,
            tokenGid = authenticator.tokenGid,
            uri = authenticator.uri,
            rawPayload = rawPayload,
        )
    }

    companion object {
        fun fromLegacy(
            token: TokenRecord,
            session: SteamSessionRecord? = null,
            fullyEnrolled: Boolean = true,
            serverTimeSeconds: Long? = null,
        ): SteamGuardAccountSnapshot {
            return SteamGuardAccountSnapshot(
                tokenId = token.id,
                accountName = token.accountName,
                authenticator = SteamAuthenticatorSecrets(
                    sharedSecret = token.sharedSecret,
                    identitySecret = token.identitySecret,
                    serialNumber = token.serialNumber,
                    revocationCode = token.revocationCode,
                    secret1 = token.secret1,
                    deviceId = token.deviceId,
                    tokenGid = token.tokenGid,
                    uri = token.uri,
                    serverTimeSeconds = serverTimeSeconds,
                    fullyEnrolled = fullyEnrolled,
                ),
                session = session,
                createdAt = token.createdAt,
                updatedAt = token.updatedAt,
                deletedAt = token.deletedAt,
            )
        }
    }
}
