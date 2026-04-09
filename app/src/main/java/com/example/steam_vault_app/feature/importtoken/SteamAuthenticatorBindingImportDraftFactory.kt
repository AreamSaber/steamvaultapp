package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult
import com.example.steam_vault_app.domain.model.ImportDraft
import com.example.steam_vault_app.domain.model.ImportSource

internal object SteamAuthenticatorBindingImportDraftFactory {
    fun from(
        preparation: SteamAuthenticatorBindingPreparation,
        beginResult: SteamAuthenticatorBeginResult,
    ): ImportDraft {
        val accountName = beginResult.accountName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: preparation.accountName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: preparation.resolvedSteamId
                ?.let { steamId -> "Steam $steamId" }
            ?: DEFAULT_ACCOUNT_NAME

        return ImportDraft(
            source = ImportSource.STEAM_BINDING,
            accountName = accountName,
            sharedSecret = beginResult.sharedSecret,
            identitySecret = beginResult.identitySecret,
            serialNumber = beginResult.serialNumber,
            revocationCode = beginResult.revocationCode,
            secret1 = beginResult.secret1,
            deviceId = preparation.generatedDeviceId,
            tokenGid = beginResult.tokenGid,
            uri = beginResult.uri,
            rawPayload = beginResult.rawResponse,
        )
    }

    private const val DEFAULT_ACCOUNT_NAME = "Steam Account"
}
