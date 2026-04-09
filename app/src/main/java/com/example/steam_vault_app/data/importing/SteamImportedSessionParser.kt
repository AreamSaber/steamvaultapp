package com.example.steam_vault_app.data.importing

import com.example.steam_vault_app.domain.model.ImportedSteamSessionDraft
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionPlatform
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

internal object SteamImportedSessionParser {
    fun parseMaFileSession(json: JSONObject): ImportedSteamSessionDraft? {
        val session = json.optJSONObject("Session")
            ?: json.optJSONObject("session")
            ?: return null
        val steamId = session.optStringAny("SteamID", "steamid", "steam_id")
            ?.filter(Char::isDigit)
        val accessToken = session.optStringAny("AccessToken", "access_token")
        val refreshToken = session.optStringAny("RefreshToken", "refresh_token")
        val sessionId = session.optStringAny("SessionID", "sessionid", "session_id")
        val oauthToken = session.optStringAny("OAuthToken", "oauth_token", "oauthToken")

        val cookies = LinkedHashMap<String, SteamSessionCookie>()
        parseExplicitCookies(session).forEach { cookie ->
            cookies[cookie.name.lowercase()] = cookie
        }
        if (!steamId.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            cookies["steamloginsecure"] = SteamSessionCookie(
                name = "steamLoginSecure",
                value = encodeSteamLoginSecure(steamId, accessToken),
            )
            cookies["mobileclient"] = SteamSessionCookie(name = "mobileClient", value = "android")
            cookies["mobileclientversion"] = SteamSessionCookie(
                name = "mobileClientVersion",
                value = MOBILE_CLIENT_VERSION,
            )
        }
        if (!sessionId.isNullOrBlank()) {
            cookies["sessionid"] = SteamSessionCookie(name = "sessionid", value = sessionId)
        }

        if (
            steamId.isNullOrBlank() &&
            accessToken.isNullOrBlank() &&
            refreshToken.isNullOrBlank() &&
            sessionId.isNullOrBlank() &&
            oauthToken.isNullOrBlank() &&
            cookies.isEmpty()
        ) {
            return null
        }

        return ImportedSteamSessionDraft(
            steamId = steamId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            oauthToken = oauthToken ?: accessToken,
            cookies = cookies.values.toList(),
            platform = SteamSessionPlatform.IMPORTED_UNKNOWN,
        )
    }

    private fun parseExplicitCookies(session: JSONObject): List<SteamSessionCookie> {
        val cookiesValue = session.opt("Cookies") ?: session.opt("cookies") ?: return emptyList()
        return when (cookiesValue) {
            is JSONObject -> buildList {
                val keys = cookiesValue.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val value = cookiesValue.optString(name).trim()
                    if (name.isNotBlank() && value.isNotBlank()) {
                        add(SteamSessionCookie(name = name, value = value))
                    }
                }
            }

            is JSONArray -> buildList {
                for (index in 0 until cookiesValue.length()) {
                    when (val item = cookiesValue.opt(index)) {
                        is JSONObject -> {
                            val name = item.optStringAny("name", "Name")
                            val value = item.optStringAny("value", "Value")
                            if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                                add(SteamSessionCookie(name = name, value = value))
                            }
                        }

                        is String -> parseCookieString(item)?.let(::add)
                    }
                }
            }

            is String -> parseCookieListText(cookiesValue)
            else -> emptyList()
        }
    }

    private fun parseCookieListText(text: String): List<SteamSessionCookie> {
        return text.split('\n', ';')
            .mapNotNull(::parseCookieString)
    }

    private fun parseCookieString(rawValue: String): SteamSessionCookie? {
        val segment = rawValue.trim()
        val separatorIndex = segment.indexOf('=')
        if (separatorIndex <= 0) {
            return null
        }
        val name = segment.substring(0, separatorIndex).trim()
        val value = segment.substring(separatorIndex + 1).trim()
        if (name.isBlank() || value.isBlank()) {
            return null
        }
        return SteamSessionCookie(name = name, value = value)
    }

    private fun encodeSteamLoginSecure(
        steamId: String,
        accessToken: String,
    ): String {
        return URLEncoder.encode(
            "$steamId||$accessToken",
            StandardCharsets.UTF_8.name(),
        )
    }

    private fun JSONObject.optStringAny(vararg keys: String): String? {
        keys.forEach { key ->
            val value = optString(key).trim()
            if (value.isNotEmpty()) {
                return value
            }
        }
        return null
    }

    private const val MOBILE_CLIENT_VERSION = "777777 3.10.3"
}
