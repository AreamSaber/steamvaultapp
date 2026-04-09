package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamMobileSession
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge.QrCode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginMode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class OkHttpSteamProtocolLoginRepositoryTest {
    @Test
    fun login_fallsBackFromConfirmation_retriesInvalidDeviceCode_andBuildsMobileSession() = runBlocking {
        val server = MockWebServer()
        server.enqueueProtoResponse(
            ProtoRsaKeyResponse(
                publicKeyMod = toEvenHex(generatedRsaPublicKey.modulus.toString(16)),
                publicKeyExp = toEvenHex(generatedRsaPublicKey.publicExponent.toString(16)),
                timestamp = 123456789L,
            ),
        )
        server.enqueueProtoResponse(
            ProtoBeginAuthSessionResponse(
                clientId = 42L,
                requestId = byteArrayOf(1, 2, 3, 4),
                pollIntervalMillis = 250L,
                allowedConfirmations = listOf(
                    ProtoAllowedConfirmation(
                        type = SteamAuthEAuthSessionGuardType.DEVICE_CONFIRMATION,
                        message = "Approve in Steam Guard",
                    ),
                    ProtoAllowedConfirmation(
                        type = SteamAuthEAuthSessionGuardType.DEVICE_CODE,
                    ),
                ),
                steamId = "76561198000000000",
                agreementSessionUrl = "https://example.com/approve",
            ),
        )
        server.enqueueEmptyResponse(eresult = SteamEresult.TWO_FACTOR_CODE_MISMATCH)
        server.enqueueEmptyResponse(eresult = SteamEresult.OK)
        server.enqueueProtoResponse(
            ProtoPollAuthSessionStatusResponse(
                refreshToken = "refresh-token",
                accountName = "demo-account",
                newGuardData = "fresh-guard-data",
            ),
        )
        server.enqueueProtoResponse(
            ProtoGenerateAccessTokenForAppResponse(
                accessToken = "generated-access-token",
            ),
        )

        server.use { mockServer ->
            val repository = OkHttpSteamProtocolLoginRepository(
                client = OkHttpClient(),
                webApiBaseUrl = mockServer.url("/").toString(),
                pollTimeoutMillis = 1_000L,
                sessionIdFactory = { "ABCDEF0123456789ABCDEF0123456789" },
            )
            val challengeSequence = mutableListOf<SteamProtocolLoginChallenge>()

            val result = repository.login(
                request = SteamProtocolLoginRequest(
                    username = "demo-account",
                    password = "p@ssword",
                ),
                respondToChallenge = { challenge ->
                    challengeSequence += challenge
                    when (challenge) {
                        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
                            SteamProtocolLoginChallengeAnswer.DeviceConfirmation(accepted = false)
                        }

                        is SteamProtocolLoginChallenge.DeviceCode -> {
                            if (challenge.previousCodeWasIncorrect) {
                                SteamProtocolLoginChallengeAnswer.Code("22222")
                            } else {
                                SteamProtocolLoginChallengeAnswer.Code("11111")
                            }
                        }

                        else -> error("Unexpected challenge $challenge")
                    }
                },
            )

            assertEquals(3, challengeSequence.size)
            assertEquals(
                SteamProtocolLoginChallenge.DeviceConfirmation(
                    confirmationUrl = "https://example.com/approve",
                ),
                challengeSequence[0],
            )
            assertEquals(
                SteamProtocolLoginChallenge.DeviceCode(
                    accountName = "demo-account",
                    previousCodeWasIncorrect = false,
                ),
                challengeSequence[1],
            )
            assertEquals(
                SteamProtocolLoginChallenge.DeviceCode(
                    accountName = "demo-account",
                    previousCodeWasIncorrect = true,
                ),
                challengeSequence[2],
            )

            assertEquals("demo-account", result.accountNameHint)
            assertEquals("fresh-guard-data", result.newGuardData)
            assertEquals("76561198000000000", result.session.steamId)
            assertEquals("generated-access-token", result.session.accessToken)
            assertEquals("refresh-token", result.session.refreshToken)
            assertEquals("fresh-guard-data", result.session.guardData)
            assertEquals("ABCDEF0123456789ABCDEF0123456789", result.session.sessionId)
            assertEquals("generated-access-token", result.session.oauthToken)
            assertTrue(
                result.session.cookies.any {
                    it.name == "steamLoginSecure" &&
                        it.value == "76561198000000000%7C%7Cgenerated-access-token"
                },
            )
            assertTrue(
                result.session.cookies.any {
                    it.name == "sessionid" &&
                        it.value == "ABCDEF0123456789ABCDEF0123456789"
                },
            )

            val rsaRequest = mockServer.takeRequest()
            assertEquals("GET", rsaRequest.method)
            assertEquals("SteamMobile", rsaRequest.requestUrl?.queryParameter("origin"))

            repeat(5) {
                val request = mockServer.takeRequest()
                assertEquals("POST", request.method)
                assertTrue(request.getHeader("Cookie")!!.contains("mobileClientVersion=777777 3.10.3"))
                assertFalse(request.body.readUtf8().isBlank())
            }
        }
    }

    @Test
    fun refreshAccessToken_updatesCookiesAndCanRenewRefreshToken() = runBlocking {
        val server = MockWebServer()
        server.enqueueProtoResponse(
            ProtoGenerateAccessTokenForAppResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
            ),
        )

        server.use { mockServer ->
            val repository = OkHttpSteamProtocolLoginRepository(
                client = OkHttpClient(),
                webApiBaseUrl = mockServer.url("/").toString(),
                sessionIdFactory = { "0123456789ABCDEF0123456789ABCDEF" },
            )

            val refreshed = repository.refreshAccessToken(
                session = SteamMobileSession(
                    steamId = "76561198000000000",
                    accessToken = "old-access-token",
                    refreshToken = "old-refresh-token",
                    guardData = "guard-data",
                ),
                allowRefreshTokenRenewal = true,
            )

            assertEquals("new-access-token", refreshed.accessToken)
            assertEquals("new-refresh-token", refreshed.refreshToken)
            assertEquals("0123456789ABCDEF0123456789ABCDEF", refreshed.sessionId)
            assertEquals("new-access-token", refreshed.oauthToken)
            assertEquals("guard-data", refreshed.guardData)
            assertTrue(refreshed.cookies.any { it.name == "mobileClient" && it.value == "android" })

            val request = mockServer.takeRequest()
            assertEquals("POST", request.method)
            assertFalse(request.body.readUtf8().isBlank())
        }
    }

    @Test
    fun login_viaQr_emitsInitialAndRefreshedChallenges_andBuildsMobileSession() = runBlocking {
        val qrRefreshToken = jwtForSubject("76561198000000000")
        val server = MockWebServer()
        server.enqueueProtoQrResponse(
            ProtoBeginAuthSessionResponse(
                clientId = 84L,
                requestId = byteArrayOf(5, 6, 7, 8),
                pollIntervalMillis = 250L,
                allowedConfirmations = emptyList(),
                steamId = "",
                challengeUrl = "https://s.team/q/initial",
                version = 7,
            ),
        )
        server.enqueueProtoResponse(
            ProtoPollAuthSessionStatusResponse(
                newClientId = 85L,
                newChallengeUrl = "https://s.team/q/refreshed",
            ),
        )
        server.enqueueProtoResponse(
            ProtoPollAuthSessionStatusResponse(
                newClientId = 86L,
                hadRemoteInteraction = true,
                refreshToken = qrRefreshToken,
                accountName = "qr-account",
            ),
        )
        server.enqueueProtoResponse(
            ProtoGenerateAccessTokenForAppResponse(
                accessToken = "qr-access-token",
            ),
        )

        server.use { mockServer ->
            val repository = OkHttpSteamProtocolLoginRepository(
                client = OkHttpClient(),
                webApiBaseUrl = mockServer.url("/").toString(),
                pollTimeoutMillis = 1_000L,
                sessionIdFactory = { "FACEFACEFACEFACEFACEFACEFACEFACE" },
            )
            val qrChallenges = mutableListOf<QrCode>()

            val result = repository.login(
                request = SteamProtocolLoginRequest(
                    username = "",
                    password = "",
                    mode = SteamProtocolLoginMode.QR_CODE,
                ),
                respondToChallenge = { challenge ->
                    error("QR login should not require blocking challenge handling: $challenge")
                },
                onQrChallengeChanged = { qrChallenge ->
                    qrChallenges += qrChallenge
                },
            )

            assertEquals(
                listOf(
                    QrCode(
                        challengeUrl = "https://s.team/q/initial",
                        refreshed = false,
                        hadRemoteInteraction = false,
                    ),
                    QrCode(
                        challengeUrl = "https://s.team/q/refreshed",
                        refreshed = true,
                        hadRemoteInteraction = false,
                    ),
                    QrCode(
                        challengeUrl = "https://s.team/q/refreshed",
                        refreshed = true,
                        hadRemoteInteraction = true,
                    ),
                ),
                qrChallenges,
            )
            assertEquals("qr-account", result.accountNameHint)
            assertEquals("76561198000000000", result.session.steamId)
            assertEquals("qr-access-token", result.session.accessToken)
            assertEquals(qrRefreshToken, result.session.refreshToken)
            assertEquals("FACEFACEFACEFACEFACEFACEFACEFACE", result.session.sessionId)

            val beginRequest = mockServer.takeRequest()
            assertEquals("POST", beginRequest.method)

            repeat(3) {
                val request = mockServer.takeRequest()
                assertEquals("POST", request.method)
            }
        }
    }

    private fun MockWebServer.enqueueProtoResponse(response: ProtoRsaKeyResponse) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", SteamEresult.OK.toString())
                .setBody(Buffer().write(SteamAuthProtobufCodec.encodeGetPasswordRsaPublicKeyResponse(response))),
        )
    }

    private fun MockWebServer.enqueueProtoResponse(response: ProtoBeginAuthSessionResponse) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", SteamEresult.OK.toString())
                .setBody(Buffer().write(SteamAuthProtobufCodec.encodeBeginAuthSessionViaCredentialsResponse(response))),
        )
    }

    private fun MockWebServer.enqueueProtoQrResponse(response: ProtoBeginAuthSessionResponse) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", SteamEresult.OK.toString())
                .setBody(Buffer().write(SteamAuthProtobufCodec.encodeBeginAuthSessionViaQrResponse(response))),
        )
    }

    private fun MockWebServer.enqueueProtoResponse(response: ProtoPollAuthSessionStatusResponse) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", SteamEresult.OK.toString())
                .setBody(Buffer().write(SteamAuthProtobufCodec.encodePollAuthSessionStatusResponse(response))),
        )
    }

    private fun MockWebServer.enqueueProtoResponse(response: ProtoGenerateAccessTokenForAppResponse) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", SteamEresult.OK.toString())
                .setBody(Buffer().write(SteamAuthProtobufCodec.encodeGenerateAccessTokenForAppResponse(response))),
        )
    }

    private fun MockWebServer.enqueueEmptyResponse(eresult: Int) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-eresult", eresult.toString()),
        )
    }

    private companion object {
        private val generatedRsaPublicKey: RSAPublicKey by lazy {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(1024)
            generator.generateKeyPair().public as RSAPublicKey
        }

        private fun toEvenHex(value: String): String {
            return if (value.length % 2 == 0) value else "0$value"
        }

        private fun jwtForSubject(steamId: String): String {
            val header = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"alg":"none"}""".toByteArray())
            val payload = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"sub":"$steamId"}""".toByteArray())
            return "$header.$payload."
        }
    }
}
