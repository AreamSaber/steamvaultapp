package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import org.json.JSONObject

object SteamAuthenticatorEnrollmentDraftCodec {
    fun encode(draft: SteamAuthenticatorEnrollmentDraft): String {
        return JSONObject()
            .put("version", 1)
            .put("steam_id", draft.steamId ?: "")
            .put("session_id", draft.sessionId)
            .put("cookies_text", draft.cookiesText)
            .put("current_url", draft.currentUrl)
            .put("captured_at", draft.capturedAt)
            .put("oauth_token", draft.oauthToken ?: "")
            .put("web_api_key", draft.webApiKey ?: "")
            .toString()
    }

    fun decode(rawDraft: String): SteamAuthenticatorEnrollmentDraft {
        val json = JSONObject(rawDraft)
        val version = json.optInt("version", 1)
        require(version == 1) {
            DataMessageCatalog.steamAuthenticatorDraftUnsupportedVersion(version)
        }
        return SteamAuthenticatorEnrollmentDraft(
            steamId = json.optString("steam_id").takeIf { it.isNotBlank() },
            sessionId = json.getString("session_id"),
            cookiesText = json.getString("cookies_text"),
            currentUrl = json.getString("current_url"),
            capturedAt = json.getString("captured_at"),
            oauthToken = json.optString("oauth_token").takeIf { it.isNotBlank() },
            webApiKey = json.optString("web_api_key").takeIf { it.isNotBlank() },
        )
    }
}
