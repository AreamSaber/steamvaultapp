package com.example.steam_vault_app.data.steam

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

internal object SteamAuthProtobufCodec {
    fun encodeGetPasswordRsaPublicKeyRequest(accountName: String): ByteArray {
        return buildMessage {
            writeString(1, accountName)
        }
    }

    fun decodeGetPasswordRsaPublicKeyResponse(bytes: ByteArray): ProtoRsaKeyResponse {
        var publicKeyMod = ""
        var publicKeyExp = ""
        var timestamp = 0L
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> publicKeyMod = input.readString()
                2 -> publicKeyExp = input.readString()
                3 -> timestamp = input.readUInt64()
                else -> input.skipField(lastTag)
            }
        }
        return ProtoRsaKeyResponse(
            publicKeyMod = publicKeyMod,
            publicKeyExp = publicKeyExp,
            timestamp = timestamp,
        )
    }

    fun encodeBeginAuthSessionViaCredentialsRequest(
        accountName: String,
        encryptedPassword: String,
        encryptionTimestamp: Long,
        rememberLogin: Boolean,
        platformType: Int,
        persistence: Int,
        websiteId: String,
        guardData: String?,
        deviceDetails: SteamAuthDeviceDetails,
    ): ByteArray {
        return buildMessage {
            writeString(2, accountName)
            writeString(3, encryptedPassword)
            writeUInt64(4, encryptionTimestamp)
            writeBool(5, rememberLogin)
            writeEnum(6, platformType)
            writeEnum(7, persistence)
            writeString(8, websiteId)
            writeByteArray(9, encodeDeviceDetails(deviceDetails))
            guardData?.takeIf { it.isNotBlank() }?.let { writeString(10, it) }
        }
    }

    fun encodeBeginAuthSessionViaQrRequest(
        websiteId: String,
        deviceDetails: SteamAuthDeviceDetails,
        platformType: Int,
    ): ByteArray {
        return buildMessage {
            writeString(1, deviceDetails.deviceFriendlyName)
            writeEnum(2, platformType)
            writeByteArray(3, encodeDeviceDetails(deviceDetails))
            writeString(4, websiteId)
        }
    }

    fun decodeBeginAuthSessionViaCredentialsResponse(bytes: ByteArray): ProtoBeginAuthSessionResponse {
        var clientId = 0L
        var requestId = ByteArray(0)
        var intervalSeconds = 0f
        val confirmations = mutableListOf<ProtoAllowedConfirmation>()
        var steamId = ""
        var agreementSessionUrl: String? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> clientId = input.readUInt64()
                2 -> requestId = input.readByteArray()
                3 -> intervalSeconds = input.readFloat()
                4 -> confirmations += decodeAllowedConfirmation(input.readByteArray())
                5 -> steamId = input.readUInt64().toString()
                7 -> agreementSessionUrl = input.readString().takeIf { it.isNotBlank() }
                else -> input.skipField(lastTag)
            }
        }

        return ProtoBeginAuthSessionResponse(
            clientId = clientId,
            requestId = requestId,
            pollIntervalMillis = (intervalSeconds * 1000).toLong().coerceAtLeast(250L),
            allowedConfirmations = confirmations,
            steamId = steamId,
            agreementSessionUrl = agreementSessionUrl,
        )
    }

    fun decodeBeginAuthSessionViaQrResponse(bytes: ByteArray): ProtoBeginAuthSessionResponse {
        var clientId = 0L
        var challengeUrl: String? = null
        var requestId = ByteArray(0)
        var intervalSeconds = 0f
        val confirmations = mutableListOf<ProtoAllowedConfirmation>()
        var version: Int? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> clientId = input.readUInt64()
                2 -> challengeUrl = input.readString().takeIf { it.isNotBlank() }
                3 -> requestId = input.readByteArray()
                4 -> intervalSeconds = input.readFloat()
                5 -> confirmations += decodeAllowedConfirmation(input.readByteArray())
                6 -> version = input.readInt32()
                else -> input.skipField(lastTag)
            }
        }

        return ProtoBeginAuthSessionResponse(
            clientId = clientId,
            requestId = requestId,
            pollIntervalMillis = (intervalSeconds * 1000).toLong().coerceAtLeast(250L),
            allowedConfirmations = confirmations,
            steamId = "",
            challengeUrl = challengeUrl,
            version = version,
        )
    }

    fun encodeGetAuthSessionInfoRequest(clientId: ULong): ByteArray {
        return buildMessage {
            writeUInt64(1, clientId.toLong())
        }
    }

    fun decodeGetAuthSessionInfoResponse(bytes: ByteArray): ProtoGetAuthSessionInfoResponse {
        var ip: String? = null
        var geoloc: String? = null
        var city: String? = null
        var state: String? = null
        var country: String? = null
        var platformType: Int? = null
        var deviceFriendlyName: String? = null
        var version: Int? = null
        var loginHistory: Int? = null
        var requestorLocationMismatch = false
        var highUsageLogin = false
        var requestedPersistence: Int? = null
        var deviceTrust: Int? = null
        var appType: Int? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> ip = input.readString().takeIf { it.isNotBlank() }
                2 -> geoloc = input.readString().takeIf { it.isNotBlank() }
                3 -> city = input.readString().takeIf { it.isNotBlank() }
                4 -> state = input.readString().takeIf { it.isNotBlank() }
                5 -> country = input.readString().takeIf { it.isNotBlank() }
                6 -> platformType = input.readEnum()
                7 -> deviceFriendlyName = input.readString().takeIf { it.isNotBlank() }
                8 -> version = input.readInt32()
                9 -> loginHistory = input.readEnum()
                10 -> requestorLocationMismatch = input.readBool()
                11 -> highUsageLogin = input.readBool()
                12 -> requestedPersistence = input.readEnum()
                13 -> deviceTrust = input.readInt32()
                14 -> appType = input.readEnum()
                else -> input.skipField(lastTag)
            }
        }

        return ProtoGetAuthSessionInfoResponse(
            ip = ip,
            geoloc = geoloc,
            city = city,
            state = state,
            country = country,
            platformType = platformType,
            deviceFriendlyName = deviceFriendlyName,
            version = version,
            loginHistory = loginHistory,
            requestorLocationMismatch = requestorLocationMismatch,
            highUsageLogin = highUsageLogin,
            requestedPersistence = requestedPersistence,
            deviceTrust = deviceTrust,
            appType = appType,
        )
    }

    fun encodeUpdateAuthSessionWithMobileConfirmationRequest(
        version: Int,
        clientId: ULong,
        steamId: Long,
        signature: ByteArray,
        confirm: Boolean,
        persistence: Int,
    ): ByteArray {
        return buildMessage {
            writeInt32(1, version)
            writeUInt64(2, clientId.toLong())
            writeFixed64(3, steamId)
            writeByteArray(4, signature)
            writeBool(5, confirm)
            writeEnum(6, persistence)
        }
    }

    fun encodeUpdateAuthSessionWithSteamGuardCodeRequest(
        clientId: Long,
        steamId: Long,
        code: String,
        codeType: Int,
    ): ByteArray {
        return buildMessage {
            writeUInt64(1, clientId)
            writeFixed64(2, steamId)
            writeString(3, code)
            writeEnum(4, codeType)
        }
    }

    fun encodePollAuthSessionStatusRequest(
        clientId: Long,
        requestId: ByteArray,
    ): ByteArray {
        return buildMessage {
            writeUInt64(1, clientId)
            writeByteArray(2, requestId)
        }
    }

    fun decodePollAuthSessionStatusResponse(bytes: ByteArray): ProtoPollAuthSessionStatusResponse {
        var newClientId: Long? = null
        var newChallengeUrl: String? = null
        var refreshToken: String? = null
        var accessToken: String? = null
        var hadRemoteInteraction = false
        var accountName: String? = null
        var newGuardData: String? = null
        var agreementSessionUrl: String? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> newClientId = input.readUInt64()
                2 -> newChallengeUrl = input.readString().takeIf { it.isNotBlank() }
                3 -> refreshToken = input.readString().takeIf { it.isNotBlank() }
                4 -> accessToken = input.readString().takeIf { it.isNotBlank() }
                5 -> hadRemoteInteraction = input.readBool()
                6 -> accountName = input.readString().takeIf { it.isNotBlank() }
                7 -> newGuardData = input.readString().takeIf { it.isNotBlank() }
                8 -> agreementSessionUrl = input.readString().takeIf { it.isNotBlank() }
                else -> input.skipField(lastTag)
            }
        }

        return ProtoPollAuthSessionStatusResponse(
            newClientId = newClientId,
            newChallengeUrl = newChallengeUrl,
            refreshToken = refreshToken,
            accessToken = accessToken,
            hadRemoteInteraction = hadRemoteInteraction,
            accountName = accountName,
            newGuardData = newGuardData,
            agreementSessionUrl = agreementSessionUrl,
        )
    }

    fun encodeGenerateAccessTokenForAppRequest(
        refreshToken: String,
        steamId: Long,
        renewalType: Int,
    ): ByteArray {
        return buildMessage {
            writeString(1, refreshToken)
            writeFixed64(2, steamId)
            writeEnum(3, renewalType)
        }
    }

    fun decodeGenerateAccessTokenForAppResponse(bytes: ByteArray): ProtoGenerateAccessTokenForAppResponse {
        var accessToken = ""
        var refreshToken: String? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> accessToken = input.readString()
                2 -> refreshToken = input.readString().takeIf { it.isNotBlank() }
                else -> input.skipField(lastTag)
            }
        }
        return ProtoGenerateAccessTokenForAppResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    internal fun encodeGetPasswordRsaPublicKeyResponse(
        response: ProtoRsaKeyResponse,
    ): ByteArray {
        return buildMessage {
            writeString(1, response.publicKeyMod)
            writeString(2, response.publicKeyExp)
            writeUInt64(3, response.timestamp)
        }
    }

    internal fun encodeBeginAuthSessionViaCredentialsResponse(
        response: ProtoBeginAuthSessionResponse,
    ): ByteArray {
        return buildMessage {
            writeUInt64(1, response.clientId)
            writeByteArray(2, response.requestId)
            writeFloat(3, response.pollIntervalMillis / 1000f)
            response.allowedConfirmations.forEach { writeByteArray(4, encodeAllowedConfirmation(it)) }
            response.steamId.toLongOrNull()?.let { writeUInt64(5, it) }
            response.agreementSessionUrl?.let { writeString(7, it) }
        }
    }

    internal fun encodeBeginAuthSessionViaQrResponse(
        response: ProtoBeginAuthSessionResponse,
    ): ByteArray {
        return buildMessage {
            writeUInt64(1, response.clientId)
            response.challengeUrl?.let { writeString(2, it) }
            writeByteArray(3, response.requestId)
            writeFloat(4, response.pollIntervalMillis / 1000f)
            response.allowedConfirmations.forEach { writeByteArray(5, encodeAllowedConfirmation(it)) }
            response.version?.let { writeInt32(6, it) }
        }
    }

    internal fun encodePollAuthSessionStatusResponse(
        response: ProtoPollAuthSessionStatusResponse,
    ): ByteArray {
        return buildMessage {
            response.newClientId?.let { writeUInt64(1, it) }
            response.newChallengeUrl?.let { writeString(2, it) }
            response.refreshToken?.let { writeString(3, it) }
            response.accessToken?.let { writeString(4, it) }
            if (response.hadRemoteInteraction) {
                writeBool(5, true)
            }
            response.accountName?.let { writeString(6, it) }
            response.newGuardData?.let { writeString(7, it) }
            response.agreementSessionUrl?.let { writeString(8, it) }
        }
    }

    internal fun encodeGenerateAccessTokenForAppResponse(
        response: ProtoGenerateAccessTokenForAppResponse,
    ): ByteArray {
        return buildMessage {
            writeString(1, response.accessToken)
            response.refreshToken?.let { writeString(2, it) }
        }
    }

    private fun encodeDeviceDetails(details: SteamAuthDeviceDetails): ByteArray {
        return buildMessage {
            writeString(1, details.deviceFriendlyName)
            writeEnum(2, details.platformType)
            details.osType?.let { writeInt32(3, it) }
            details.gamingDeviceType?.let { writeUInt32(4, it) }
        }
    }

    private fun encodeAllowedConfirmation(confirmation: ProtoAllowedConfirmation): ByteArray {
        return buildMessage {
            writeEnum(1, confirmation.type)
            confirmation.message?.let { writeString(2, it) }
        }
    }

    private fun decodeAllowedConfirmation(bytes: ByteArray): ProtoAllowedConfirmation {
        var type = SteamAuthEAuthSessionGuardType.UNKNOWN
        var message: String? = null
        readMessage(bytes) { input, fieldNumber ->
            when (fieldNumber) {
                1 -> type = input.readEnum()
                2 -> message = input.readString().takeIf { it.isNotBlank() }
                else -> input.skipField(lastTag)
            }
        }
        return ProtoAllowedConfirmation(type = type, message = message)
    }

    private inline fun buildMessage(
        block: CodedOutputStream.() -> Unit,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val codedOutput = CodedOutputStream.newInstance(output)
        codedOutput.block()
        codedOutput.flush()
        return output.toByteArray()
    }

    private inline fun readMessage(
        bytes: ByteArray,
        block: MessageReader.(CodedInputStream, Int) -> Unit,
    ) {
        val input = CodedInputStream.newInstance(bytes)
        val reader = MessageReader()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) {
                break
            }
            reader.lastTag = tag
            block(reader, input, tag ushr 3)
        }
    }

    private class MessageReader {
        var lastTag: Int = 0
    }
}

internal object SteamAuthPlatformType {
    const val WEB_BROWSER = 2
    const val MOBILE_APP = 3
}

internal object SteamAuthSessionPersistence {
    const val EPHEMERAL = 0
    const val PERSISTENT = 1
}

internal object SteamAuthTokenRenewalType {
    const val NONE = 0
    const val ALLOW = 1
}

internal object SteamAuthEAuthSessionGuardType {
    const val UNKNOWN = 0
    const val NONE = 1
    const val EMAIL_CODE = 2
    const val DEVICE_CODE = 3
    const val DEVICE_CONFIRMATION = 4
    const val EMAIL_CONFIRMATION = 5
    const val MACHINE_TOKEN = 6
}

internal object SteamAuthOsType {
    const val ANDROID_UNKNOWN = -500
}

internal object SteamEresult {
    const val OK = 1
    const val FAIL = 2
    const val INVALID_PASSWORD = 5
    const val FILE_NOT_FOUND = 9
    const val ACCESS_DENIED = 15
    const val SERVICE_UNAVAILABLE = 20
    const val EXPIRED = 27
    const val DUPLICATE_REQUEST = 29
    const val INVALID_LOGIN_AUTH_CODE = 65
    const val RATE_LIMIT_EXCEEDED = 84
    const val TWO_FACTOR_CODE_MISMATCH = 88
}

internal fun describeEresult(eresult: Int): String {
    return when (eresult) {
        SteamEresult.OK -> "EResult.OK (1)"
        SteamEresult.FAIL -> "EResult.Fail (2)"
        SteamEresult.INVALID_PASSWORD -> "EResult.InvalidPassword (5)"
        SteamEresult.FILE_NOT_FOUND -> "EResult.FileNotFound (9)"
        SteamEresult.ACCESS_DENIED -> "EResult.AccessDenied (15)"
        SteamEresult.SERVICE_UNAVAILABLE -> "EResult.ServiceUnavailable (20)"
        SteamEresult.EXPIRED -> "EResult.Expired (27)"
        SteamEresult.DUPLICATE_REQUEST -> "EResult.DuplicateRequest (29)"
        SteamEresult.INVALID_LOGIN_AUTH_CODE -> "EResult.InvalidLoginAuthCode (65)"
        SteamEresult.RATE_LIMIT_EXCEEDED -> "EResult.RateLimitExceeded (84)"
        SteamEresult.TWO_FACTOR_CODE_MISMATCH -> "EResult.TwoFactorCodeMismatch (88)"
        else -> "EResult($eresult)"
    }
}

internal data class ProtoRsaKeyResponse(
    val publicKeyMod: String,
    val publicKeyExp: String,
    val timestamp: Long,
)

internal data class SteamAuthDeviceDetails(
    val deviceFriendlyName: String,
    val platformType: Int,
    val osType: Int? = null,
    val gamingDeviceType: Int? = null,
)

internal data class ProtoAllowedConfirmation(
    val type: Int,
    val message: String? = null,
)

internal data class ProtoBeginAuthSessionResponse(
    val clientId: Long,
    val requestId: ByteArray,
    val pollIntervalMillis: Long,
    val allowedConfirmations: List<ProtoAllowedConfirmation>,
    val steamId: String,
    val challengeUrl: String? = null,
    val version: Int? = null,
    val agreementSessionUrl: String? = null,
)

internal data class ProtoPollAuthSessionStatusResponse(
    val newClientId: Long? = null,
    val newChallengeUrl: String? = null,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val hadRemoteInteraction: Boolean = false,
    val accountName: String? = null,
    val newGuardData: String? = null,
    val agreementSessionUrl: String? = null,
)

internal data class ProtoGenerateAccessTokenForAppResponse(
    val accessToken: String,
    val refreshToken: String? = null,
)

internal data class ProtoGetAuthSessionInfoResponse(
    val ip: String? = null,
    val geoloc: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val platformType: Int? = null,
    val deviceFriendlyName: String? = null,
    val version: Int? = null,
    val loginHistory: Int? = null,
    val requestorLocationMismatch: Boolean = false,
    val highUsageLogin: Boolean = false,
    val requestedPersistence: Int? = null,
    val deviceTrust: Int? = null,
    val appType: Int? = null,
)
