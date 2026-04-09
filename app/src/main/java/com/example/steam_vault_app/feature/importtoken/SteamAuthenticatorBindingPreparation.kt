package com.example.steam_vault_app.feature.importtoken

import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.feature.steamsession.SteamSessionEditorParser
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal enum class SteamAuthenticatorBindingSourceKind {
    ENROLLMENT_DRAFT,
    PROTOCOL_LOGIN,
}

internal enum class SteamAuthenticatorBindingRequirement {
    SESSION_ID_COOKIE,
    STEAM_LOGIN_SECURE_COOKIE,
    STEAM_ID,
    API_AUTHENTICATION,
}

internal data class SteamAuthenticatorBindingPreparation(
    val sourceKind: SteamAuthenticatorBindingSourceKind,
    val sourceSignature: String,
    val draft: SteamAuthenticatorEnrollmentDraft? = null,
    val bindingContext: SteamAuthenticatorBindingContext? = null,
    val sessionId: String,
    val capturedAt: String,
    val currentUrl: String? = null,
    val accountName: String? = null,
    val cookies: List<SteamSessionCookie>,
    val sessionIdCookie: SteamSessionCookie?,
    val steamLoginSecureCookie: SteamSessionCookie?,
    val steamLoginCookie: SteamSessionCookie?,
    val resolvedSteamId: String?,
    val oauthToken: String?,
    val webApiKey: String?,
    val generatedDeviceId: String?,
    val missingRequirements: List<SteamAuthenticatorBindingRequirement>,
) {
    init {
        require((draft == null) != (bindingContext == null)) {
            "Binding preparation must be created from exactly one source."
        }
    }

    val isReadyForBinding: Boolean
        get() = missingRequirements.isEmpty()

    val cookieNames: List<String>
        get() = cookies.map { it.name }.distinct().sorted()

    val supportsEditableWebApiKey: Boolean
        get() = draft != null || bindingContext != null
}

internal object SteamAuthenticatorBindingPreparationFactory {
    fun from(draft: SteamAuthenticatorEnrollmentDraft): SteamAuthenticatorBindingPreparation {
        val cookies = SteamSessionEditorParser.parseCookies(draft.cookiesText)
        val sessionIdCookie = cookies.lastOrNull { it.name.equals("sessionid", ignoreCase = true) }
        val steamLoginSecureCookie = cookies.lastOrNull {
            it.name.equals("steamLoginSecure", ignoreCase = true)
        }
        val steamLoginCookie = cookies.lastOrNull {
            it.name.equals("steamLogin", ignoreCase = true)
        }
        val resolvedSteamId = draft.steamId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extractSteamId(steamLoginSecureCookie?.value)
            ?: extractSteamId(steamLoginCookie?.value)
        val oauthToken = draft.oauthToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extractOauthToken(cookies)
        val webApiKey = draft.webApiKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val generatedDeviceId = resolvedSteamId?.let(SteamMobileDeviceId::fromSteamId)

        val missingRequirements = buildList {
            if (sessionIdCookie == null) {
                add(SteamAuthenticatorBindingRequirement.SESSION_ID_COOKIE)
            }
            if (steamLoginSecureCookie == null) {
                add(SteamAuthenticatorBindingRequirement.STEAM_LOGIN_SECURE_COOKIE)
            }
            if (resolvedSteamId == null) {
                add(SteamAuthenticatorBindingRequirement.STEAM_ID)
            }
            if (oauthToken == null && webApiKey == null) {
                add(SteamAuthenticatorBindingRequirement.API_AUTHENTICATION)
            }
        }

        return SteamAuthenticatorBindingPreparation(
            sourceKind = SteamAuthenticatorBindingSourceKind.ENROLLMENT_DRAFT,
            sourceSignature = draft.signature,
            draft = draft,
            sessionId = draft.sessionId,
            capturedAt = draft.capturedAt,
            currentUrl = draft.currentUrl,
            cookies = cookies,
            sessionIdCookie = sessionIdCookie,
            steamLoginSecureCookie = steamLoginSecureCookie,
            steamLoginCookie = steamLoginCookie,
            resolvedSteamId = resolvedSteamId,
            oauthToken = oauthToken,
            webApiKey = webApiKey,
            generatedDeviceId = generatedDeviceId,
            missingRequirements = missingRequirements,
        )
    }

    fun from(context: SteamAuthenticatorBindingContext): SteamAuthenticatorBindingPreparation {
        val cookies = context.session.cookies
        val sessionIdCookie = cookies.lastOrNull { it.name.equals("sessionid", ignoreCase = true) }
        val steamLoginSecureCookie = cookies.lastOrNull {
            it.name.equals("steamLoginSecure", ignoreCase = true)
        }
        val steamLoginCookie = cookies.lastOrNull {
            it.name.equals("steamLogin", ignoreCase = true)
        }
        val resolvedSteamId = context.session.steamId.trim().takeIf { it.isNotEmpty() }
            ?: extractSteamId(steamLoginSecureCookie?.value)
            ?: extractSteamId(steamLoginCookie?.value)
        val oauthToken = context.session.oauthToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.session.accessToken.trim().takeIf { it.isNotEmpty() }
        val webApiKey = context.webApiKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val generatedDeviceId = resolvedSteamId?.let(SteamMobileDeviceId::fromSteamId)
        val sessionId = context.session.sessionId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: sessionIdCookie?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ""

        val missingRequirements = buildList {
            if (sessionIdCookie == null && sessionId.isBlank()) {
                add(SteamAuthenticatorBindingRequirement.SESSION_ID_COOKIE)
            }
            if (steamLoginSecureCookie == null) {
                add(SteamAuthenticatorBindingRequirement.STEAM_LOGIN_SECURE_COOKIE)
            }
            if (resolvedSteamId == null) {
                add(SteamAuthenticatorBindingRequirement.STEAM_ID)
            }
            if (oauthToken == null && webApiKey == null) {
                add(SteamAuthenticatorBindingRequirement.API_AUTHENTICATION)
            }
        }

        return SteamAuthenticatorBindingPreparation(
            sourceKind = SteamAuthenticatorBindingSourceKind.PROTOCOL_LOGIN,
            sourceSignature = context.signature,
            bindingContext = context,
            sessionId = sessionId,
            capturedAt = context.capturedAt,
            accountName = context.accountName,
            cookies = cookies,
            sessionIdCookie = sessionIdCookie,
            steamLoginSecureCookie = steamLoginSecureCookie,
            steamLoginCookie = steamLoginCookie,
            resolvedSteamId = resolvedSteamId,
            oauthToken = oauthToken,
            webApiKey = webApiKey,
            generatedDeviceId = generatedDeviceId,
            missingRequirements = missingRequirements,
        )
    }

    private fun extractSteamId(rawCookieValue: String?): String? {
        val normalized = rawCookieValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::decodeCookieValue)
            ?: return null

        val steamId = normalized.substringBefore("||").trim()
        return steamId.takeIf { it.all(Char::isDigit) }
    }

    private fun decodeCookieValue(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            value
        }
    }

    private fun extractOauthToken(cookies: List<SteamSessionCookie>): String? {
        return cookies.lastOrNull {
            it.name.equals("oauth_token", ignoreCase = true) ||
                it.name.equals("oauthToken", ignoreCase = true) ||
                it.name.equals("access_token", ignoreCase = true)
        }?.value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
