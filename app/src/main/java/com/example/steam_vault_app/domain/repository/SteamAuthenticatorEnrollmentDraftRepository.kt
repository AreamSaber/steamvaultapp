package com.example.steam_vault_app.domain.repository

import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft

interface SteamAuthenticatorEnrollmentDraftRepository {
    suspend fun getDraft(): SteamAuthenticatorEnrollmentDraft?

    suspend fun saveDraft(draft: SteamAuthenticatorEnrollmentDraft)

    suspend fun clearDraft()
}
