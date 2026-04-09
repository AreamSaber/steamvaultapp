package com.example.steam_vault_app.data.steam

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

internal class OkHttpSteamWebApiClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val webApiBaseUrl: String = DEFAULT_WEBAPI_BASE_URL,
) {
    suspend fun getPasswordRsaPublicKey(accountName: String): ProtoRsaKeyResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "GetPasswordRSAPublicKey",
            httpMethod = SteamWebApiHttpMethod.GET,
            encodedRequest = SteamAuthProtobufCodec.encodeGetPasswordRsaPublicKeyRequest(accountName),
        )
        requireSuccess(apiMethod = "GetPasswordRSAPublicKey", response = response)
        return SteamAuthProtobufCodec.decodeGetPasswordRsaPublicKeyResponse(response.responseBytes)
    }

    suspend fun beginAuthSessionViaCredentials(
        request: BeginAuthSessionWithCredentialsRequest,
    ): ProtoBeginAuthSessionResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "BeginAuthSessionViaCredentials",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodeBeginAuthSessionViaCredentialsRequest(
                accountName = request.accountName,
                encryptedPassword = request.encryptedPassword,
                encryptionTimestamp = request.encryptionTimestamp,
                rememberLogin = request.rememberLogin,
                platformType = request.platformType,
                persistence = request.persistence,
                websiteId = request.websiteId,
                guardData = request.guardData,
                deviceDetails = request.deviceDetails,
            ),
        )
        requireSuccess(apiMethod = "BeginAuthSessionViaCredentials", response = response)
        return SteamAuthProtobufCodec.decodeBeginAuthSessionViaCredentialsResponse(response.responseBytes)
    }

    suspend fun beginAuthSessionViaQr(
        request: BeginAuthSessionViaQrRequest,
    ): ProtoBeginAuthSessionResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "BeginAuthSessionViaQR",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodeBeginAuthSessionViaQrRequest(
                websiteId = request.websiteId,
                deviceDetails = request.deviceDetails,
                platformType = request.platformType,
            ),
        )
        requireSuccess(apiMethod = "BeginAuthSessionViaQR", response = response)
        return SteamAuthProtobufCodec.decodeBeginAuthSessionViaQrResponse(response.responseBytes)
    }

    suspend fun getAuthSessionInfo(
        clientId: ULong,
        accessToken: String,
    ): ProtoGetAuthSessionInfoResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "GetAuthSessionInfo",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodeGetAuthSessionInfoRequest(clientId),
            accessToken = accessToken,
        )
        requireSuccess(apiMethod = "GetAuthSessionInfo", response = response)
        return SteamAuthProtobufCodec.decodeGetAuthSessionInfoResponse(response.responseBytes)
    }

    suspend fun updateAuthSessionWithMobileConfirmation(
        request: UpdateAuthSessionWithMobileConfirmationRequest,
        accessToken: String,
    ) {
        val response = sendAuthenticationRequest(
            apiMethod = "UpdateAuthSessionWithMobileConfirmation",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec
                .encodeUpdateAuthSessionWithMobileConfirmationRequest(
                    version = request.version,
                    clientId = request.clientId,
                    steamId = request.steamId,
                    signature = request.signature,
                    confirm = request.confirm,
                    persistence = request.persistence,
                ),
            accessToken = accessToken,
        )
        if (response.eresult !in setOf(SteamEresult.OK, SteamEresult.DUPLICATE_REQUEST)) {
            throw buildApiException(
                apiMethod = "UpdateAuthSessionWithMobileConfirmation",
                response = response,
            )
        }
    }

    suspend fun updateAuthSessionWithSteamGuardCode(
        clientId: Long,
        steamId: Long,
        code: String,
        codeType: Int,
    ): Int {
        val response = sendAuthenticationRequest(
            apiMethod = "UpdateAuthSessionWithSteamGuardCode",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodeUpdateAuthSessionWithSteamGuardCodeRequest(
                clientId = clientId,
                steamId = steamId,
                code = code,
                codeType = codeType,
            ),
        )
        if (
            response.eresult !in setOf(
                SteamEresult.OK,
                SteamEresult.DUPLICATE_REQUEST,
                SteamEresult.INVALID_LOGIN_AUTH_CODE,
                SteamEresult.TWO_FACTOR_CODE_MISMATCH,
                SteamEresult.EXPIRED,
            )
        ) {
            throw buildApiException(
                apiMethod = "UpdateAuthSessionWithSteamGuardCode",
                response = response,
            )
        }
        return response.eresult
    }

    suspend fun pollAuthSessionStatus(
        clientId: Long,
        requestId: ByteArray,
    ): ProtoPollAuthSessionStatusResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "PollAuthSessionStatus",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodePollAuthSessionStatusRequest(
                clientId = clientId,
                requestId = requestId,
            ),
        )
        requireSuccess(apiMethod = "PollAuthSessionStatus", response = response)
        return SteamAuthProtobufCodec.decodePollAuthSessionStatusResponse(response.responseBytes)
    }

    suspend fun generateAccessTokenForApp(
        refreshToken: String,
        steamId: Long,
        allowRefreshTokenRenewal: Boolean,
    ): ProtoGenerateAccessTokenForAppResponse {
        val response = sendAuthenticationRequest(
            apiMethod = "GenerateAccessTokenForApp",
            httpMethod = SteamWebApiHttpMethod.POST,
            encodedRequest = SteamAuthProtobufCodec.encodeGenerateAccessTokenForAppRequest(
                refreshToken = refreshToken,
                steamId = steamId,
                renewalType = if (allowRefreshTokenRenewal) {
                    SteamAuthTokenRenewalType.ALLOW
                } else {
                    SteamAuthTokenRenewalType.NONE
                },
            ),
        )
        requireSuccess(apiMethod = "GenerateAccessTokenForApp", response = response)
        return SteamAuthProtobufCodec.decodeGenerateAccessTokenForAppResponse(response.responseBytes)
    }

    private suspend fun sendAuthenticationRequest(
        apiMethod: String,
        httpMethod: SteamWebApiHttpMethod,
        encodedRequest: ByteArray?,
        accessToken: String? = null,
    ): SteamWebApiResponse = withContext(Dispatchers.IO) {
        val encodedPayload = encodedRequest
            ?.takeIf { it.isNotEmpty() }
            ?.let { Base64.getEncoder().encodeToString(it) }
        val httpUrl = buildSteamWebApiUrl(
            apiMethod = apiMethod,
            httpMethod = httpMethod,
            encodedPayload = encodedPayload,
            accessToken = accessToken,
        )
        val requestBuilder = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/json, text/plain, */*")
            .header("sec-fetch-site", "cross-site")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-dest", "empty")
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Cookie", MOBILE_API_COOKIE_HEADER)

        when (httpMethod) {
            SteamWebApiHttpMethod.GET -> requestBuilder.get()
            SteamWebApiHttpMethod.POST -> {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .apply {
                        encodedPayload?.let { addFormDataPart("input_protobuf_encoded", it) }
                    }
                    .build()
                requestBuilder.post(requestBody)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    buildString {
                        append("Steam WebAPI ")
                        append(apiMethod)
                        append(" failed with HTTP ")
                        append(response.code)
                        responseBytes
                            .toString(StandardCharsets.UTF_8)
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                append(": ")
                                append(it.take(MAX_ERROR_BODY_LENGTH))
                            }
                    },
                )
            }

            val eresult = response.header("x-eresult")?.toIntOrNull()
                ?: throw IllegalStateException(
                    "Steam WebAPI response for $apiMethod is missing x-eresult.",
                )

            SteamWebApiResponse(
                eresult = eresult,
                errorMessage = response.header("x-error_message")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::decodeSteamErrorHeader),
                responseBytes = responseBytes,
            )
        }
    }

    private fun buildSteamWebApiUrl(
        apiMethod: String,
        httpMethod: SteamWebApiHttpMethod,
        encodedPayload: String?,
        accessToken: String?,
    ) = webApiBaseUrl.toHttpUrl().newBuilder()
        .addPathSegment("IAuthenticationService")
        .addPathSegment(apiMethod)
        .addPathSegment("v1")
        .apply {
            accessToken?.takeIf { it.isNotBlank() }?.let { addQueryParameter("access_token", it) }
            if (httpMethod == SteamWebApiHttpMethod.GET) {
                encodedPayload?.let { addQueryParameter("input_protobuf_encoded", it) }
                addQueryParameter("origin", "SteamMobile")
            }
        }
        .build()

    private fun requireSuccess(
        apiMethod: String,
        response: SteamWebApiResponse,
    ) {
        if (response.eresult != SteamEresult.OK) {
            throw buildApiException(apiMethod, response)
        }
    }

    private fun buildApiException(
        apiMethod: String,
        response: SteamWebApiResponse,
    ): SteamWebApiException {
        val message = response.errorMessage
            ?: "Steam WebAPI $apiMethod failed with ${describeEresult(response.eresult)}."
        return SteamWebApiException(message = message, eresult = response.eresult)
    }

    private fun decodeSteamErrorHeader(rawHeader: String): String {
        return runCatching {
            URLDecoder.decode(rawHeader, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawHeader)
    }

    private companion object {
        private const val DEFAULT_WEBAPI_BASE_URL = "https://api.steampowered.com/"
        private const val MAX_ERROR_BODY_LENGTH = 200
        private const val MOBILE_USER_AGENT = "okhttp/4.9.2"
        private const val MOBILE_CLIENT_VERSION = "777777 3.10.3"
        private const val MOBILE_API_COOKIE_HEADER =
            "mobileClient=android; mobileClientVersion=$MOBILE_CLIENT_VERSION"
    }
}

internal data class BeginAuthSessionWithCredentialsRequest(
    val accountName: String,
    val encryptedPassword: String,
    val encryptionTimestamp: Long,
    val rememberLogin: Boolean,
    val platformType: Int,
    val persistence: Int,
    val websiteId: String,
    val guardData: String?,
    val deviceDetails: SteamAuthDeviceDetails,
)

internal data class BeginAuthSessionViaQrRequest(
    val websiteId: String,
    val platformType: Int,
    val deviceDetails: SteamAuthDeviceDetails,
)

internal data class SteamWebApiResponse(
    val eresult: Int,
    val errorMessage: String?,
    val responseBytes: ByteArray,
)

internal data class UpdateAuthSessionWithMobileConfirmationRequest(
    val version: Int,
    val clientId: ULong,
    val steamId: Long,
    val signature: ByteArray,
    val confirm: Boolean,
    val persistence: Int,
)

internal class SteamWebApiException(
    message: String,
    val eresult: Int,
) : IllegalStateException(message)

internal enum class SteamWebApiHttpMethod {
    GET,
    POST,
}
