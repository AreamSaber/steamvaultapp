package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.security.SteamSecretCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object SteamQrLoginSigner {
    private const val HMAC_SHA256 = "HmacSHA256"

    fun generateSignature(
        sharedSecret: String,
        version: Int,
        clientId: ULong,
        steamId: Long,
    ): ByteArray {
        require(version >= 0) { "Steam QR login version cannot be negative." }
        require(steamId >= 0L) { "SteamID cannot be negative." }

        val secretBytes = SteamSecretCodec.decode(sharedSecret)
        return try {
            val payload = ByteBuffer.allocate(18)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((version and 0xFFFF).toShort())
                .putLong(clientId.toLong())
                .putLong(steamId)
                .array()
            val hmac = Mac.getInstance(HMAC_SHA256)
            hmac.init(SecretKeySpec(secretBytes, HMAC_SHA256))
            hmac.doFinal(payload)
        } finally {
            secretBytes.fill(0)
        }
    }
}
