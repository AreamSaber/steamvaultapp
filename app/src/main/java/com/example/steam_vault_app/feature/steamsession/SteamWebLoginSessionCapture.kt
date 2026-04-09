package com.example.steam_vault_app.feature.steamsession

import com.example.steam_vault_app.domain.model.SteamSessionCookie
import java.io.Serializable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class SteamWebLoginCapture(
    val steamId: String?,
    val sessionId: String,
    val cookiesText: String,
    val currentUrl: String,
    val oauthToken: String? = null,
) {
    val signature: String
        get() = "$sessionId|$steamId|$cookiesText|${oauthToken.orEmpty()}"
}

internal data class SteamWebLoginObservation(
    val currentUrl: String,
    val cookieHeader: String?,
    val pageTitle: String?,
    val pageTextSnippet: String?,
)

internal enum class SteamWebLoginStage {
    WAITING_FOR_CREDENTIALS,
    EMAIL_CODE_REQUIRED,
    TWO_FACTOR_REQUIRED,
    ADDITIONAL_CHALLENGE_REQUIRED,
    SESSION_READY,
    UNKNOWN,
}

internal enum class SteamWebLoginFailureReason {
    INVALID_CREDENTIALS,
    INVALID_EMAIL_CODE,
    INVALID_TWO_FACTOR_CODE,
    RATE_LIMITED,
    CAPTCHA_REQUIRED,
}

internal data class SteamWebLoginAnalysis(
    val stage: SteamWebLoginStage,
    val failureReason: SteamWebLoginFailureReason? = null,
) : Serializable

internal object SteamWebLoginSessionCapture {
    // 使用 store.steampowered.com 登录页面（新版）
    const val loginUrl = "https://store.steampowered.com/login/?redir=&redir_ssl=1"
    // 登录成功后跳转的社区页面
    const val cookiesUrl = "https://steamcommunity.com/"

    fun captureFromCookieHeader(
        cookieHeader: String?,
        currentUrl: String,
    ): SteamWebLoginCapture? {
        if (cookieHeader.isNullOrBlank()) {
            return null
        }

        val cookies = cookieHeader.split(';')
            .mapNotNull { rawSegment ->
                val segment = rawSegment.trim()
                if (segment.isEmpty()) {
                    return@mapNotNull null
                }
                val separatorIndex = segment.indexOf('=')
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }
                val name = segment.substring(0, separatorIndex).trim()
                val value = segment.substring(separatorIndex + 1).trim()
                if (name.isEmpty() || value.isEmpty()) {
                    return@mapNotNull null
                }
                SteamSessionCookie(name = name, value = value)
            }

        if (cookies.isEmpty()) {
            return null
        }

        val sessionId = cookies.firstOrNull { it.name.equals("sessionid", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val authCookie = cookies.firstOrNull {
            it.name.equals("steamLoginSecure", ignoreCase = true) ||
                it.name.equals("steamLogin", ignoreCase = true)
        } ?: return null

        return SteamWebLoginCapture(
            steamId = deriveSteamId(authCookie.value),
            sessionId = sessionId,
            cookiesText = SteamSessionEditorParser.formatCookies(cookies),
            currentUrl = currentUrl,
            oauthToken = cookies.firstOrNull {
                it.name.equals("oauth_token", ignoreCase = true) ||
                    it.name.equals("oauthToken", ignoreCase = true) ||
                    it.name.equals("access_token", ignoreCase = true)
            }?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun detectStage(observation: SteamWebLoginObservation): SteamWebLoginStage {
        return analyzeObservation(observation).stage
    }

    fun analyzeObservation(observation: SteamWebLoginObservation): SteamWebLoginAnalysis {
        if (captureFromCookieHeader(observation.cookieHeader, observation.currentUrl) != null) {
            return SteamWebLoginAnalysis(stage = SteamWebLoginStage.SESSION_READY)
        }

        val currentUrl = observation.currentUrl.lowercase()
        val combinedSignals = buildString {
            append(currentUrl)
            append(' ')
            append(observation.pageTitle.orEmpty())
            append(' ')
            append(observation.pageTextSnippet.orEmpty())
        }.lowercase()

        val stage = when {
            looksLikeTwoFactorStep(currentUrl, combinedSignals) -> {
                SteamWebLoginStage.TWO_FACTOR_REQUIRED
            }

            looksLikeEmailCodeStep(currentUrl, combinedSignals) -> {
                SteamWebLoginStage.EMAIL_CODE_REQUIRED
            }

            looksLikeAdditionalChallenge(combinedSignals) -> {
                SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED
            }

            looksLikeCredentialEntry(currentUrl, combinedSignals) -> {
                SteamWebLoginStage.WAITING_FOR_CREDENTIALS
            }

            else -> SteamWebLoginStage.UNKNOWN
        }

        val failureReason = when {
            looksLikeRateLimited(combinedSignals) -> {
                SteamWebLoginFailureReason.RATE_LIMITED
            }

            looksLikeCaptchaRequired(combinedSignals) -> {
                SteamWebLoginFailureReason.CAPTCHA_REQUIRED
            }

            stage == SteamWebLoginStage.WAITING_FOR_CREDENTIALS &&
                looksLikeInvalidCredentials(combinedSignals) -> {
                SteamWebLoginFailureReason.INVALID_CREDENTIALS
            }

            stage == SteamWebLoginStage.EMAIL_CODE_REQUIRED &&
                looksLikeInvalidCode(combinedSignals) -> {
                SteamWebLoginFailureReason.INVALID_EMAIL_CODE
            }

            stage == SteamWebLoginStage.TWO_FACTOR_REQUIRED &&
                looksLikeInvalidCode(combinedSignals) -> {
                SteamWebLoginFailureReason.INVALID_TWO_FACTOR_CODE
            }

            else -> null
        }

        return SteamWebLoginAnalysis(
            stage = stage,
            failureReason = failureReason,
        )
    }

    private fun deriveSteamId(rawAuthCookie: String): String? {
        val decoded = runCatching {
            URLDecoder.decode(rawAuthCookie, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawAuthCookie)
        val candidate = decoded.substringBefore("||").trim()
        return candidate.takeIf { value ->
            value.isNotEmpty() && value.all(Char::isDigit)
        }
    }

    private fun looksLikeCredentialEntry(
        currentUrl: String,
        combinedSignals: String,
    ): Boolean {
        if (currentUrl.contains("/login/home")) {
            return true
        }
        return listOf(
            "sign in",
            "steam sign in",
            "account name",
            "password",
            "help, i can't sign in",
            "登录",
            "账号名称",
            "密码",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeEmailCodeStep(
        currentUrl: String,
        combinedSignals: String,
    ): Boolean {
        if (currentUrl.contains("checkemail")) {
            return true
        }
        val hasEmailSignal = listOf(
            "email",
            "e-mail",
            "mail code",
            "邮箱",
            "电子邮件",
            "邮件",
        ).any(combinedSignals::contains)
        val hasCodeSignal = listOf(
            "steam guard",
            "guard code",
            "code",
            "验证码",
            "确认是你本人",
        ).any(combinedSignals::contains)
        return hasEmailSignal && hasCodeSignal
    }

    private fun looksLikeTwoFactorStep(
        currentUrl: String,
        combinedSignals: String,
    ): Boolean {
        if (currentUrl.contains("twofactor")) {
            return true
        }
        return listOf(
            "mobile authenticator",
            "authenticator",
            "steam guard mobile",
            "two-factor",
            "two factor",
            "手机令牌",
            "身份验证器",
            "动态验证码",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeAdditionalChallenge(combinedSignals: String): Boolean {
        return listOf(
            "captcha",
            "verify you are human",
            "human verification",
            "额外验证",
            "人机验证",
            "完成验证",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeInvalidCredentials(combinedSignals: String): Boolean {
        return listOf(
            "incorrect account name or password",
            "account name or password that you have entered is incorrect",
            "please check your password and account name",
            "密码错误",
            "账号名称或密码错误",
            "用户名或密码错误",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeInvalidCode(combinedSignals: String): Boolean {
        return listOf(
            "incorrect code",
            "invalid code",
            "code is incorrect",
            "the code you entered is invalid",
            "验证码错误",
            "验证码无效",
            "代码不正确",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeRateLimited(combinedSignals: String): Boolean {
        return listOf(
            "too many login failures",
            "too many attempts",
            "rate limit",
            "please wait and try again later",
            "尝试次数过多",
            "稍后再试",
        ).any(combinedSignals::contains)
    }

    private fun looksLikeCaptchaRequired(combinedSignals: String): Boolean {
        return listOf(
            "captcha",
            "verify you are human",
            "human verification",
            "i am not a robot",
            "人机验证",
        ).any(combinedSignals::contains)
    }
}
