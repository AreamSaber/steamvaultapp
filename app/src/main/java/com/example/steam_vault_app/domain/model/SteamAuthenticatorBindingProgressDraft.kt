package com.example.steam_vault_app.domain.model

import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult

enum class SteamAuthenticatorBindingProgressStage {
    MATERIAL_READY,
    WAITING_NEXT_ACTIVATION_CODE,
}

data class SteamAuthenticatorBindingProgressDraft(
    val enrollmentDraftSignature: String,
    val begunAt: String,
    val beginResult: SteamAuthenticatorBeginResult,
    val serverTimeOffsetSeconds: Long = 0L,
    val stage: SteamAuthenticatorBindingProgressStage =
        SteamAuthenticatorBindingProgressStage.MATERIAL_READY,
    val lastUpdatedAt: String = begunAt,
    val statusMessage: String? = null,
)
