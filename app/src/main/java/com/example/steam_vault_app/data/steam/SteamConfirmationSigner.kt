package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.DataMessageCatalog
import com.example.steam_vault_app.data.security.SteamSecretCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SteamConfirmationSigner {
    private const val HMAC_SHA1 = "HmacSHA1"
    private const val MAX_TAG_BYTES = 32

    fun generateConfirmationKey(
        identitySecret: String,
        timestampSeconds: Long,
        tag: String,
    ): String {
        require(timestampSeconds >= 0L) { DataMessageCatalog.steamConfirmationNegativeTimestamp() }
        val normalizedTag = tag.trim()
        require(normalizedTag.isNotEmpty()) { DataMessageCatalog.steamConfirmationBlankTag() }

        val tagBytes = normalizedTag.toByteArray(StandardCharsets.UTF_8)
        require(tagBytes.size <= MAX_TAG_BYTES) { DataMessageCatalog.steamConfirmationTagTooLong() }

        val secretBytes = SteamSecretCodec.decode(identitySecret)
        return try {
            val payload = ByteBuffer.allocate(8 + tagBytes.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(timestampSeconds)
                .put(tagBytes)
                .array()
            val hmac = Mac.getInstance(HMAC_SHA1)
            hmac.init(SecretKeySpec(secretBytes, HMAC_SHA1))
            Base64.getEncoder().encodeToString(hmac.doFinal(payload))
        } finally {
            secretBytes.fill(0)
        }
    }
}
