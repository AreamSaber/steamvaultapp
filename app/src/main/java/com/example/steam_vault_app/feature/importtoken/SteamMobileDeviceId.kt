package com.example.steam_vault_app.feature.importtoken

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object SteamMobileDeviceId {
    fun fromSteamId(steamId: String): String {
        require(steamId.isNotBlank()) { "SteamID cannot be blank." }

        val digest = MessageDigest.getInstance("SHA-1")
            .digest(steamId.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(32)

        return buildString {
            append("android:")
            append(digest.substring(0, 8))
            append('-')
            append(digest.substring(8, 12))
            append('-')
            append(digest.substring(12, 16))
            append('-')
            append(digest.substring(16, 20))
            append('-')
            append(digest.substring(20, 32))
        }
    }
}
