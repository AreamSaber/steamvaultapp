package com.example.steam_vault_app.domain.model

enum class SteamProtocolLoginMode {
    INITIAL,
    QR_CODE,
    REFRESH,
    IMPORT,
}

data class SteamProtocolLoginRequest(
    val username: String,
    val password: String,
    val mode: SteamProtocolLoginMode = SteamProtocolLoginMode.INITIAL,
    val existingAccount: SteamGuardAccountSnapshot? = null,
    val persistentSession: Boolean = false,
    val guardData: String? = null,
    val websiteId: String = "Mobile",
)

sealed interface SteamProtocolLoginChallenge {
    data class QrCode(
        val challengeUrl: String,
        val refreshed: Boolean = false,
        val hadRemoteInteraction: Boolean = false,
    ) : SteamProtocolLoginChallenge

    data class EmailCode(
        val emailAddress: String,
        val previousCodeWasIncorrect: Boolean = false,
    ) : SteamProtocolLoginChallenge

    data class DeviceCode(
        val accountName: String? = null,
        val previousCodeWasIncorrect: Boolean = false,
    ) : SteamProtocolLoginChallenge

    data class DeviceConfirmation(
        val confirmationUrl: String? = null,
    ) : SteamProtocolLoginChallenge
}

sealed interface SteamProtocolLoginChallengeAnswer {
    data object QrCodeReady : SteamProtocolLoginChallengeAnswer

    data class Code(val value: String) : SteamProtocolLoginChallengeAnswer

    data class DeviceConfirmation(val accepted: Boolean) : SteamProtocolLoginChallengeAnswer

    data object Cancelled : SteamProtocolLoginChallengeAnswer
}

data class SteamProtocolLoginResult(
    val session: SteamMobileSession,
    val accountNameHint: String? = null,
    val newGuardData: String? = null,
)
