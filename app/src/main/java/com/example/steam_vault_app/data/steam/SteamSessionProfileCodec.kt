package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionPlatform
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import org.json.JSONArray
import org.json.JSONObject

data class SteamSessionProfile(
    val sessions: List<SteamSessionRecord>,
)

object SteamSessionProfileCodec {
    private const val CURRENT_VERSION = 2

    fun encode(profile: SteamSessionProfile): String {
        val sessionsJson = JSONArray()
        profile.sessions.forEach { session ->
            val cookiesJson = JSONArray()
            session.cookies.forEach { cookie ->
                cookiesJson.put(
                    JSONObject()
                        .put("name", cookie.name)
                        .put("value", cookie.value),
                )
            }

            sessionsJson.put(
                JSONObject()
                    .put("token_id", session.tokenId)
                    .put("account_name", session.accountName)
                    .put("steam_id", session.steamId ?: "")
                    .put("access_token", session.accessToken ?: "")
                    .put("refresh_token", session.refreshToken ?: "")
                    .put("guard_data", session.guardData ?: "")
                    .put("session_id", session.sessionId ?: "")
                    .put("cookies", cookiesJson)
                    .put("oauth_token", session.oauthToken ?: "")
                    .put("platform", session.platform.name)
                    .put("created_at", session.createdAt)
                    .put("updated_at", session.updatedAt)
                    .put("last_validated_at", session.lastValidatedAt ?: "")
                    .put("validation_status", session.validationStatus.name)
                    .put("last_validation_error_message", session.lastValidationErrorMessage ?: ""),
            )
        }

        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("sessions", sessionsJson)
            .toString()
    }

    fun decode(rawProfile: String): SteamSessionProfile {
        val json = JSONObject(rawProfile)
        val version = json.optInt("version", 1)
        require(version in 1..CURRENT_VERSION) {
            DataMessageCatalog.steamSessionProfileUnsupportedVersion(version)
        }

        val sessions = buildList {
            val sessionsJson = json.optJSONArray("sessions") ?: JSONArray()
            for (index in 0 until sessionsJson.length()) {
                val sessionJson = sessionsJson.getJSONObject(index)
                add(
                    SteamSessionRecord(
                        tokenId = sessionJson.getString("token_id"),
                        accountName = sessionJson.getString("account_name"),
                        steamId = sessionJson.optString("steam_id").takeIf { it.isNotBlank() },
                        accessToken = sessionJson.optString("access_token").takeIf { it.isNotBlank() },
                        refreshToken = sessionJson.optString("refresh_token").takeIf { it.isNotBlank() },
                        guardData = sessionJson.optString("guard_data").takeIf { it.isNotBlank() },
                        sessionId = sessionJson.optString("session_id").takeIf { it.isNotBlank() },
                        cookies = buildList {
                            val cookiesJson = sessionJson.optJSONArray("cookies") ?: JSONArray()
                            for (cookieIndex in 0 until cookiesJson.length()) {
                                val cookieJson = cookiesJson.getJSONObject(cookieIndex)
                                add(
                                    SteamSessionCookie(
                                        name = cookieJson.getString("name"),
                                        value = cookieJson.getString("value"),
                                    ),
                                )
                            }
                        },
                        oauthToken = sessionJson.optString("oauth_token").takeIf { it.isNotBlank() },
                        platform = sessionJson.optString("platform")
                            .takeIf { it.isNotBlank() }
                            ?.let { platform ->
                                runCatching { SteamSessionPlatform.valueOf(platform) }
                                    .getOrDefault(
                                        if (version == 1) {
                                            SteamSessionPlatform.WEB_BROWSER
                                        } else {
                                            SteamSessionPlatform.IMPORTED_UNKNOWN
                                        },
                                    )
                            }
                            ?: if (version == 1) {
                                SteamSessionPlatform.WEB_BROWSER
                            } else {
                                SteamSessionPlatform.IMPORTED_UNKNOWN
                            },
                        createdAt = sessionJson.getString("created_at"),
                        updatedAt = sessionJson.getString("updated_at"),
                        lastValidatedAt = sessionJson.optString("last_validated_at")
                            .takeIf { it.isNotBlank() },
                        validationStatus = sessionJson.optString("validation_status")
                            .takeIf { it.isNotBlank() }
                            ?.let { status ->
                                runCatching { SteamSessionValidationStatus.valueOf(status) }
                                    .getOrDefault(SteamSessionValidationStatus.UNKNOWN)
                            }
                            ?: SteamSessionValidationStatus.UNKNOWN,
                        lastValidationErrorMessage = sessionJson.optString("last_validation_error_message")
                            .takeIf { it.isNotBlank() },
                    ),
                )
            }
        }

        return SteamSessionProfile(sessions = sessions)
    }
}
