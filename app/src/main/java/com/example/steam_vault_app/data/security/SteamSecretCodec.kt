package com.example.steam_vault_app.data.security

import com.example.steam_vault_app.data.DataMessageCatalog
import java.util.Base64

object SteamSecretCodec {
    fun normalizeToBase64(rawSecret: String): String {
        return Base64.getEncoder().encodeToString(decode(rawSecret))
    }

    fun decode(rawSecret: String): ByteArray {
        val sanitized = rawSecret.trim()
        require(sanitized.isNotBlank()) { DataMessageCatalog.steamSecretBlank() }

        if (sanitized.looksLikeBase32Secret()) {
            decodeBase32OrNull(sanitized)?.let { return it }
            decodeBase64OrNull(sanitized)?.let { return it }
        } else {
            decodeBase64OrNull(sanitized)?.let { return it }
            decodeBase32OrNull(sanitized)?.let { return it }
        }

        throw IllegalArgumentException(
            DataMessageCatalog.steamSecretInvalid(),
        )
    }

    private fun decodeBase64OrNull(rawSecret: String): ByteArray? {
        return try {
            val normalized = rawSecret.replace("\\s".toRegex(), "")
            val padded = normalized.padEnd(
                normalized.length + ((4 - (normalized.length % 4)) % 4),
                '=',
            )
            val decoded = Base64.getDecoder().decode(padded)
            if (decoded.isEmpty()) {
                null
            } else {
                decoded
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decodeBase32OrNull(rawSecret: String): ByteArray? {
        val normalized = rawSecret
            .uppercase()
            .replace("=", "")
            .replace("\\s".toRegex(), "")

        if (normalized.isEmpty()) {
            return null
        }

        val output = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (char in normalized) {
            val value = BASE32_ALPHABET.indexOf(char)
            if (value == -1) {
                return null
            }

            buffer = (buffer shl 5) or value
            bitsLeft += 5

            while (bitsLeft >= 8) {
                output += ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }

        if (output.isEmpty()) {
            return null
        }

        return output.toByteArray()
    }

    private fun String.looksLikeBase32Secret(): Boolean {
        val normalized = uppercase()
            .replace("=", "")
            .replace("\\s".toRegex(), "")
        return normalized.isNotEmpty() &&
            normalized.length % 8 == 0 &&
            normalized.all { it in BASE32_ALPHABET }
    }

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
}
