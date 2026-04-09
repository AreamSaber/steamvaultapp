package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContextSource
import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionPlatform
import org.json.JSONArray
import org.json.JSONObject

object SteamAuthenticatorBindingContextCodec {
    private const val CURRENT_VERSION = 1

    fun encode(context: SteamAuthenticatorBindingContext): String {
        val cookiesJson = JSONArray()
        context.session.cookies.forEach { cookie ->
            cookiesJson.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value),
            )
        }

        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("account_name", context.accountName)
            .put("captured_at", context.capturedAt)
            .put("web_api_key", context.webApiKey ?: "")
            .put("source", context.source.name)
            .put(
                "session",
                JSONObject()
                    .put("steam_id", context.session.steamId)
                    .put("access_token", context.session.accessToken)
                    .put("refresh_token", context.session.refreshToken)
                    .put("guard_data", context.session.guardData ?: "")
                    .put("session_id", context.session.sessionId ?: "")
                    .put("oauth_token", context.session.oauthToken ?: "")
                    .put("platform", context.session.platform.name)
                    .put("cookies", cookiesJson),
            )
            .toString()
    }

    fun decode(rawContext: String): SteamAuthenticatorBindingContext {
        val json = JSONObject(rawContext)
        val version = json.optInt("version", 0)
        require(version == CURRENT_VERSION) {
            DataMessageCatalog.steamAuthenticatorBindingContextUnsupportedVersion(version)
        }

        val sessionJson = json.optJSONObject("session")
            ?: throw IllegalArgumentException(
                DataMessageCatalog.steamAuthenticatorBindingContextMissingSession(),
            )

        val cookies = buildList {
            val cookiesJson = sessionJson.optJSONArray("cookies") ?: JSONArray()
            for (index in 0 until cookiesJson.length()) {
                val cookieJson = cookiesJson.getJSONObject(index)
                add(
                    SteamSessionCookie(
                        name = cookieJson.getString("name"),
                        value = cookieJson.getString("value"),
                    ),
                )
            }
        }

        return SteamAuthenticatorBindingContext(
            accountName = json.getString("account_name"),
            capturedAt = json.getString("captured_at"),
            webApiKey = json.optString("web_api_key").takeIf { it.isNotBlank() },
            source = json.optString("source")
                .takeIf { it.isNotBlank() }
                ?.let { value ->
                    runCatching { SteamAuthenticatorBindingContextSource.valueOf(value) }
                        .getOrDefault(SteamAuthenticatorBindingContextSource.PROTOCOL_LOGIN)
                }
                ?: SteamAuthenticatorBindingContextSource.PROTOCOL_LOGIN,
            session = SteamMobileSession(
                steamId = sessionJson.getString("steam_id"),
                accessToken = sessionJson.getString("access_token"),
                refreshToken = sessionJson.getString("refresh_token"),
                guardData = sessionJson.optString("guard_data").takeIf { it.isNotBlank() },
                sessionId = sessionJson.optString("session_id").takeIf { it.isNotBlank() },
                oauthToken = sessionJson.optString("oauth_token").takeIf { it.isNotBlank() },
                cookies = cookies,
                platform = sessionJson.optString("platform")
                    .takeIf { it.isNotBlank() }
                    ?.let { value ->
                        runCatching { SteamSessionPlatform.valueOf(value) }
                            .getOrDefault(SteamSessionPlatform.MOBILE_APP)
                    }
                    ?: SteamSessionPlatform.MOBILE_APP,
            ),
        )
    }
}
