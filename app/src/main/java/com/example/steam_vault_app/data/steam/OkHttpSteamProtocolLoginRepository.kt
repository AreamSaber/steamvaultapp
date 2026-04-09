package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge.QrCode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamProtocolLoginResult
import com.example.steam_vault_app.domain.model.SteamProtocolLoginMode
import com.example.steam_vault_app.domain.model.SteamSessionCookie
import com.example.steam_vault_app.domain.model.SteamSessionPlatform
import com.example.steam_vault_app.domain.repository.SteamProtocolLoginRepository
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

internal class OkHttpSteamProtocolLoginRepository(
    private val steamWebApiClient: OkHttpSteamWebApiClient = OkHttpSteamWebApiClient(),
    private val pollTimeoutMillis: Long = DEFAULT_POLL_TIMEOUT_MILLIS,
    private val sessionIdFactory: () -> String = ::generateSessionId,
) : SteamProtocolLoginRepository {
    constructor(
        client: OkHttpClient,
        webApiBaseUrl: String,
        pollTimeoutMillis: Long = DEFAULT_POLL_TIMEOUT_MILLIS,
        sessionIdFactory: () -> String = ::generateSessionId,
    ) : this(
        steamWebApiClient = OkHttpSteamWebApiClient(
            client = client,
            webApiBaseUrl = webApiBaseUrl,
        ),
        pollTimeoutMillis = pollTimeoutMillis,
        sessionIdFactory = sessionIdFactory,
    )

    override suspend fun login(
        request: SteamProtocolLoginRequest,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
        onQrChallengeChanged: suspend (QrCode) -> Unit,
    ): SteamProtocolLoginResult {
        return when (request.mode) {
            SteamProtocolLoginMode.QR_CODE -> loginViaQr(
                request = request,
                onQrChallengeChanged = onQrChallengeChanged,
            )

            else -> loginViaCredentials(
                request = request,
                respondToChallenge = respondToChallenge,
            )
        }
    }

    private suspend fun loginViaCredentials(
        request: SteamProtocolLoginRequest,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
    ): SteamProtocolLoginResult {
        require(request.username.isNotBlank()) { "Steam username is empty." }
        require(request.password.isNotBlank()) { "Steam password is empty." }

        val rsaKey = steamWebApiClient.getPasswordRsaPublicKey(request.username)
        val encryptedPassword = encryptPassword(
            password = request.password,
            rsaKey = rsaKey,
        )
        val startedSession = steamWebApiClient.beginAuthSessionViaCredentials(
            BeginAuthSessionWithCredentialsRequest(
                accountName = request.username,
                encryptedPassword = encryptedPassword,
                encryptionTimestamp = rsaKey.timestamp,
                rememberLogin = request.persistentSession,
                platformType = SteamAuthPlatformType.MOBILE_APP,
                persistence = if (request.persistentSession) {
                    SteamAuthSessionPersistence.PERSISTENT
                } else {
                    SteamAuthSessionPersistence.EPHEMERAL
                },
                websiteId = request.websiteId.ifBlank { DEFAULT_WEBSITE_ID },
                guardData = request.guardData,
                deviceDetails = SteamAuthDeviceDetails(
                    deviceFriendlyName = MOBILE_DEVICE_FRIENDLY_NAME,
                    platformType = SteamAuthPlatformType.MOBILE_APP,
                    osType = SteamAuthOsType.ANDROID_UNKNOWN,
                    gamingDeviceType = MOBILE_GAMING_DEVICE_TYPE,
                ),
            ),
        )

        val pollResponse = completeLogin(
            request = request,
            startedSession = startedSession,
            respondToChallenge = respondToChallenge,
        )
        return buildLoginResult(
            request = request,
            startedSession = startedSession,
            pollResponse = pollResponse,
        )
    }

    private suspend fun loginViaQr(
        request: SteamProtocolLoginRequest,
        onQrChallengeChanged: suspend (QrCode) -> Unit,
    ): SteamProtocolLoginResult {
        val startedSession = steamWebApiClient.beginAuthSessionViaQr(
            BeginAuthSessionViaQrRequest(
                websiteId = request.websiteId.ifBlank { DEFAULT_WEBSITE_ID },
                platformType = SteamAuthPlatformType.MOBILE_APP,
                deviceDetails = SteamAuthDeviceDetails(
                    deviceFriendlyName = MOBILE_DEVICE_FRIENDLY_NAME,
                    platformType = SteamAuthPlatformType.MOBILE_APP,
                    osType = SteamAuthOsType.ANDROID_UNKNOWN,
                    gamingDeviceType = MOBILE_GAMING_DEVICE_TYPE,
                ),
            ),
        )
        var qrChallengeState = QrCode(
            challengeUrl = startedSession.challengeUrl?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Steam QR login did not return a challenge URL."),
        )
        onQrChallengeChanged(qrChallengeState)

        val pollResponse = pollUntilAuthenticated(
            startedSession = startedSession,
            onIntermediateResponse = { response ->
                val refreshedChallengeUrl = response.newChallengeUrl?.takeIf { it.isNotBlank() }
                val nextChallengeUrl = refreshedChallengeUrl ?: qrChallengeState.challengeUrl
                val nextHadRemoteInteraction =
                    qrChallengeState.hadRemoteInteraction || response.hadRemoteInteraction
                if (
                    nextChallengeUrl != qrChallengeState.challengeUrl ||
                    nextHadRemoteInteraction != qrChallengeState.hadRemoteInteraction
                ) {
                    qrChallengeState = qrChallengeState.copy(
                        challengeUrl = nextChallengeUrl,
                        refreshed = nextChallengeUrl != qrChallengeState.challengeUrl,
                        hadRemoteInteraction = nextHadRemoteInteraction,
                    )
                    onQrChallengeChanged(qrChallengeState)
                }
            },
        )
        return buildLoginResult(
            request = request,
            startedSession = startedSession,
            pollResponse = pollResponse,
        )
    }

    override suspend fun refreshAccessToken(
        session: SteamMobileSession,
        allowRefreshTokenRenewal: Boolean,
    ): SteamMobileSession {
        require(session.refreshToken.isNotBlank()) { "Steam refresh token is empty." }

        val steamId = session.steamId.ifBlank {
            decodeJwtSubject(session.refreshToken)
                ?: throw IllegalStateException("Steam refresh token does not contain a SteamID.")
        }
        val refreshedTokens = steamWebApiClient.generateAccessTokenForApp(
            refreshToken = session.refreshToken,
            steamId = steamId.toLong(),
            allowRefreshTokenRenewal = allowRefreshTokenRenewal,
        )
        val accessToken = refreshedTokens.accessToken.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Steam did not return an access token.")
        val refreshToken = refreshedTokens.refreshToken?.takeIf { it.isNotBlank() } ?: session.refreshToken
        val sessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: sessionIdFactory()

        return session.copy(
            steamId = steamId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            oauthToken = accessToken,
            cookies = buildMobileSessionCookies(
                steamId = steamId,
                sessionId = sessionId,
                accessToken = accessToken,
            ),
            platform = SteamSessionPlatform.MOBILE_APP,
        )
    }

    private suspend fun completeLogin(
        request: SteamProtocolLoginRequest,
        startedSession: ProtoBeginAuthSessionResponse,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
    ): ProtoPollAuthSessionStatusResponse {
        val confirmations = startedSession.allowedConfirmations
            .sortedBy { confirmationSortOrder(it.type) }
            .ifEmpty { listOf(ProtoAllowedConfirmation(type = SteamAuthEAuthSessionGuardType.NONE)) }

        var confirmationIndex = 0
        while (confirmationIndex < confirmations.size) {
            val confirmation = confirmations[confirmationIndex]
            when (confirmation.type) {
                SteamAuthEAuthSessionGuardType.NONE -> {
                    return pollUntilAuthenticated(startedSession)
                }

                SteamAuthEAuthSessionGuardType.DEVICE_CONFIRMATION,
                SteamAuthEAuthSessionGuardType.EMAIL_CONFIRMATION,
                -> {
                    when (
                        val answer = respondToChallenge(
                            SteamProtocolLoginChallenge.DeviceConfirmation(
                                confirmationUrl = startedSession.agreementSessionUrl
                                    ?: confirmation.message,
                            ),
                        )
                    ) {
                        is SteamProtocolLoginChallengeAnswer.DeviceConfirmation -> {
                            if (answer.accepted) {
                                return pollUntilAuthenticated(startedSession)
                            }
                            confirmationIndex += 1
                        }

                        SteamProtocolLoginChallengeAnswer.Cancelled -> {
                            throw IllegalStateException("Steam login was cancelled.")
                        }

                        else -> {
                            throw IllegalStateException(
                                "Steam login requires approval in Steam Guard before continuing.",
                            )
                        }
                    }
                }

                SteamAuthEAuthSessionGuardType.DEVICE_CODE,
                SteamAuthEAuthSessionGuardType.EMAIL_CODE,
                -> {
                    return completeCodeChallenge(
                        request = request,
                        startedSession = startedSession,
                        confirmation = confirmation,
                        respondToChallenge = respondToChallenge,
                    )
                }

                SteamAuthEAuthSessionGuardType.MACHINE_TOKEN -> {
                    confirmationIndex += 1
                }

                else -> {
                    throw IllegalStateException(
                        "Steam login requires an unsupported challenge type ${confirmation.type}.",
                    )
                }
            }
        }

        throw IllegalStateException("Steam login requires a challenge that this build cannot satisfy.")
    }

    private suspend fun completeCodeChallenge(
        request: SteamProtocolLoginRequest,
        startedSession: ProtoBeginAuthSessionResponse,
        confirmation: ProtoAllowedConfirmation,
        respondToChallenge: suspend (SteamProtocolLoginChallenge) -> SteamProtocolLoginChallengeAnswer,
    ): ProtoPollAuthSessionStatusResponse {
        val steamId = startedSession.steamId.toLongOrNull()
            ?: throw IllegalStateException("Steam login session did not include a SteamID.")
        var previousCodeWasIncorrect = false

        while (true) {
            val challenge = when (confirmation.type) {
                SteamAuthEAuthSessionGuardType.EMAIL_CODE -> {
                    SteamProtocolLoginChallenge.EmailCode(
                        emailAddress = confirmation.message.orEmpty(),
                        previousCodeWasIncorrect = previousCodeWasIncorrect,
                    )
                }

                SteamAuthEAuthSessionGuardType.DEVICE_CODE -> {
                    SteamProtocolLoginChallenge.DeviceCode(
                        accountName = request.existingAccount?.accountName ?: request.username,
                        previousCodeWasIncorrect = previousCodeWasIncorrect,
                    )
                }

                else -> error("Unexpected code challenge type ${confirmation.type}.")
            }

            when (val answer = respondToChallenge(challenge)) {
                is SteamProtocolLoginChallengeAnswer.Code -> {
                    val result = steamWebApiClient.updateAuthSessionWithSteamGuardCode(
                        clientId = startedSession.clientId,
                        steamId = steamId,
                        code = answer.value.trim(),
                        codeType = confirmation.type,
                    )
                    when (result) {
                        SteamEresult.OK,
                        SteamEresult.DUPLICATE_REQUEST,
                        -> return pollUntilAuthenticated(startedSession)

                        invalidCodeResultFor(confirmation.type) -> previousCodeWasIncorrect = true

                        SteamEresult.EXPIRED -> {
                            throw SteamWebApiException(
                                message = "Steam login challenge expired. Please start over.",
                                eresult = result,
                            )
                        }

                        else -> {
                            throw SteamWebApiException(
                                message = "Steam login code submission failed with ${describeEresult(result)}.",
                                eresult = result,
                            )
                        }
                    }
                }

                SteamProtocolLoginChallengeAnswer.Cancelled -> {
                    throw IllegalStateException("Steam login was cancelled.")
                }

                else -> {
                    throw IllegalStateException("Steam login requires a Steam Guard code.")
                }
            }
        }
    }

    private suspend fun pollUntilAuthenticated(
        startedSession: ProtoBeginAuthSessionResponse,
        onIntermediateResponse: suspend (ProtoPollAuthSessionStatusResponse) -> Unit = {},
    ): ProtoPollAuthSessionStatusResponse {
        val deadlineMillis = System.currentTimeMillis() + pollTimeoutMillis
        var clientId = startedSession.clientId

        while (true) {
            val response = steamWebApiClient.pollAuthSessionStatus(
                clientId = clientId,
                requestId = startedSession.requestId,
            )
            clientId = response.newClientId ?: clientId
            onIntermediateResponse(response)
            if (!response.refreshToken.isNullOrBlank()) {
                return response.copy(newClientId = clientId)
            }
            if (System.currentTimeMillis() >= deadlineMillis) {
                throw IllegalStateException("Steam login timed out before authentication completed.")
            }
            delay(startedSession.pollIntervalMillis.coerceAtLeast(MIN_POLL_INTERVAL_MILLIS))
        }
    }

    private suspend fun buildLoginResult(
        request: SteamProtocolLoginRequest,
        startedSession: ProtoBeginAuthSessionResponse,
        pollResponse: ProtoPollAuthSessionStatusResponse,
    ): SteamProtocolLoginResult {
        val steamId = startedSession.steamId.ifBlank {
            decodeJwtSubject(pollResponse.refreshToken)
                ?: throw IllegalStateException("Steam login did not include a SteamID.")
        }
        val initialRefreshToken = pollResponse.refreshToken
            ?: throw IllegalStateException("Steam login did not return a refresh token.")
        val generatedAccessTokens = if (pollResponse.accessToken.isNullOrBlank()) {
            steamWebApiClient.generateAccessTokenForApp(
                refreshToken = initialRefreshToken,
                steamId = steamId.toLong(),
                allowRefreshTokenRenewal = false,
            )
        } else {
            null
        }
        val accessToken = pollResponse.accessToken
            ?.takeIf { it.isNotBlank() }
            ?: generatedAccessTokens?.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Steam login did not return an access token.")
        val refreshToken = generatedAccessTokens?.refreshToken?.takeIf { it.isNotBlank() }
            ?: initialRefreshToken
        val guardData = pollResponse.newGuardData?.takeIf { it.isNotBlank() }
            ?: request.guardData?.takeIf { it.isNotBlank() }
        val sessionId = sessionIdFactory()

        return SteamProtocolLoginResult(
            session = SteamMobileSession(
                steamId = steamId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                guardData = guardData,
                sessionId = sessionId,
                oauthToken = accessToken,
                cookies = buildMobileSessionCookies(
                    steamId = steamId,
                    sessionId = sessionId,
                    accessToken = accessToken,
                ),
                platform = SteamSessionPlatform.MOBILE_APP,
            ),
            accountNameHint = pollResponse.accountName?.takeIf { it.isNotBlank() }
                ?: request.existingAccount?.accountName?.takeIf { it.isNotBlank() }
                ?: request.username.takeIf { it.isNotBlank() },
            newGuardData = pollResponse.newGuardData?.takeIf { it.isNotBlank() },
        )
    }

    private fun encryptPassword(
        password: String,
        rsaKey: ProtoRsaKeyResponse,
    ): String {
        val publicKeySpec = RSAPublicKeySpec(
            BigInteger(1, hexToByteArray(rsaKey.publicKeyMod)),
            BigInteger(1, hexToByteArray(rsaKey.publicKeyExp)),
        )
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(password.toByteArray(Charsets.UTF_8)),
        )
    }

    private fun buildMobileSessionCookies(
        steamId: String,
        sessionId: String,
        accessToken: String,
    ): List<SteamSessionCookie> {
        return listOf(
            SteamSessionCookie(
                name = "steamLoginSecure",
                value = "$steamId%7C%7C$accessToken",
            ),
            SteamSessionCookie(name = "sessionid", value = sessionId),
            SteamSessionCookie(name = "mobileClient", value = "android"),
            SteamSessionCookie(name = "mobileClientVersion", value = MOBILE_CLIENT_VERSION),
        )
    }

    private fun invalidCodeResultFor(codeType: Int): Int {
        return when (codeType) {
            SteamAuthEAuthSessionGuardType.EMAIL_CODE -> SteamEresult.INVALID_LOGIN_AUTH_CODE
            SteamAuthEAuthSessionGuardType.DEVICE_CODE -> SteamEresult.TWO_FACTOR_CODE_MISMATCH
            else -> SteamEresult.FAIL
        }
    }

    private fun confirmationSortOrder(type: Int): Int {
        return when (type) {
            SteamAuthEAuthSessionGuardType.NONE -> 0
            SteamAuthEAuthSessionGuardType.DEVICE_CONFIRMATION -> 1
            SteamAuthEAuthSessionGuardType.DEVICE_CODE -> 2
            SteamAuthEAuthSessionGuardType.EMAIL_CODE -> 3
            SteamAuthEAuthSessionGuardType.EMAIL_CONFIRMATION -> 4
            SteamAuthEAuthSessionGuardType.MACHINE_TOKEN -> 5
            else -> Int.MAX_VALUE
        }
    }

    private fun decodeJwtSubject(token: String?): String? {
        if (token.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val payload = token.split(".").getOrNull(1) ?: return null
            val padded = payload.padEnd(
                payload.length + ((4 - payload.length % 4) % 4),
                '=',
            )
            val decoded = Base64.getUrlDecoder().decode(padded)
            JWT_SUBJECT_REGEX.find(String(decoded, Charsets.UTF_8))
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private companion object {
        private const val DEFAULT_POLL_TIMEOUT_MILLIS = 30_000L
        private const val MIN_POLL_INTERVAL_MILLIS = 250L
        private const val DEFAULT_WEBSITE_ID = "Mobile"
        private const val MOBILE_DEVICE_FRIENDLY_NAME = "Galaxy S25"
        private const val MOBILE_GAMING_DEVICE_TYPE = 528
        private const val MOBILE_CLIENT_VERSION = "777777 3.10.3"
        private val JWT_SUBJECT_REGEX = Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"")
        private val sessionIdRandom = SecureRandom()

        private fun generateSessionId(): String {
            val bytes = ByteArray(16)
            sessionIdRandom.nextBytes(bytes)
            return bytes.joinToString(separator = "") { byte -> "%02X".format(byte) }
        }

        private fun hexToByteArray(value: String): ByteArray {
            require(value.length % 2 == 0) { "Steam RSA key contained invalid hex data." }
            return ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
