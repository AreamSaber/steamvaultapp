package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressDraft
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressStage
import org.json.JSONObject

object SteamAuthenticatorBindingProgressDraftCodec {
    fun encode(draft: SteamAuthenticatorBindingProgressDraft): String {
        return JSONObject()
            .put("version", 1)
            .put("enrollment_draft_signature", draft.enrollmentDraftSignature)
            .put("begun_at", draft.begunAt)
            .put("server_time_offset_seconds", draft.serverTimeOffsetSeconds)
            .put("stage", draft.stage.name)
            .put("last_updated_at", draft.lastUpdatedAt)
            .put("status_message", draft.statusMessage ?: "")
            .put(
                "begin_result",
                JSONObject()
                    .put("steam_id", draft.beginResult.steamId)
                    .put("account_name", draft.beginResult.accountName ?: "")
                    .put("shared_secret", draft.beginResult.sharedSecret)
                    .put("identity_secret", draft.beginResult.identitySecret ?: "")
                    .put("serial_number", draft.beginResult.serialNumber ?: "")
                    .put("revocation_code", draft.beginResult.revocationCode ?: "")
                    .put("secret_1", draft.beginResult.secret1 ?: "")
                    .put("token_gid", draft.beginResult.tokenGid ?: "")
                    .put("uri", draft.beginResult.uri ?: "")
                    .put("server_time_seconds", draft.beginResult.serverTimeSeconds ?: JSONObject.NULL)
                    .put("status", draft.beginResult.status ?: JSONObject.NULL)
                    .put("fully_enrolled", draft.beginResult.fullyEnrolled)
                    .put("raw_response", draft.beginResult.rawResponse ?: ""),
            )
            .toString()
    }

    fun decode(encodedDraft: String): SteamAuthenticatorBindingProgressDraft {
        val json = JSONObject(encodedDraft)
        val version = json.optInt("version", 0)
        require(version == 1) {
            DataMessageCatalog.steamAuthenticatorBindingProgressUnsupportedVersion(version)
        }
        val beginResultJson = json.optJSONObject("begin_result")
            ?: throw IllegalArgumentException(
                DataMessageCatalog.steamAuthenticatorBindingProgressMissingBeginResult(),
            )

        return SteamAuthenticatorBindingProgressDraft(
            enrollmentDraftSignature = json.getString("enrollment_draft_signature"),
            begunAt = json.getString("begun_at"),
            serverTimeOffsetSeconds = json.optLong("server_time_offset_seconds", 0L),
            stage = json.optString("stage")
                .takeIf { it.isNotBlank() }
                ?.let {
                    runCatching { SteamAuthenticatorBindingProgressStage.valueOf(it) }
                        .getOrDefault(SteamAuthenticatorBindingProgressStage.MATERIAL_READY)
                }
                ?: SteamAuthenticatorBindingProgressStage.MATERIAL_READY,
            lastUpdatedAt = json.optString("last_updated_at")
                .takeIf { it.isNotBlank() }
                ?: json.getString("begun_at"),
            statusMessage = json.optString("status_message").takeIf { it.isNotBlank() },
            beginResult = SteamAuthenticatorBeginResult(
                steamId = beginResultJson.getString("steam_id"),
                accountName = beginResultJson.optString("account_name").takeIf { it.isNotBlank() },
                sharedSecret = beginResultJson.getString("shared_secret"),
                identitySecret = beginResultJson.optString("identity_secret")
                    .takeIf { it.isNotBlank() },
                serialNumber = beginResultJson.optString("serial_number").takeIf { it.isNotBlank() },
                revocationCode = beginResultJson.optString("revocation_code")
                    .takeIf { it.isNotBlank() },
                secret1 = beginResultJson.optString("secret_1").takeIf { it.isNotBlank() },
                tokenGid = beginResultJson.optString("token_gid").takeIf { it.isNotBlank() },
                uri = beginResultJson.optString("uri").takeIf { it.isNotBlank() },
                serverTimeSeconds = beginResultJson.optLong("server_time_seconds")
                    .takeIf { beginResultJson.has("server_time_seconds") },
                status = beginResultJson.optInt("status")
                    .takeIf { beginResultJson.has("status") },
                fullyEnrolled = beginResultJson.optBoolean("fully_enrolled", false),
                rawResponse = beginResultJson.optString("raw_response").takeIf { it.isNotBlank() },
            ),
        )
    }
}
