package com.example.steam_vault_app.feature.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.example.steam_vault_app.data.security.SteamSecretCodec
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.security.VaultCryptography
import kotlinx.coroutines.delay

data class SteamCodeSnapshot(
    val codeDisplay: String,
    val secondsRemaining: Int,
    val progress: Float,
    val hasSecretError: Boolean,
)

@Composable
fun rememberCurrentEpochSeconds(
    offsetSeconds: Long = 0L,
): Long {
    val currentEpochSeconds by produceState(
        initialValue = currentSteamEpochSeconds(offsetSeconds),
        key1 = offsetSeconds,
    ) {
        while (true) {
            value = currentSteamEpochSeconds(offsetSeconds)
            delay(1_000L)
        }
    }

    return currentEpochSeconds
}

private fun currentSteamEpochSeconds(offsetSeconds: Long): Long {
    return (System.currentTimeMillis() / 1000L) + offsetSeconds
}

fun buildSteamCodeSnapshot(
    token: TokenRecord,
    currentEpochSeconds: Long,
    vaultCryptography: VaultCryptography,
    errorCodeDisplay: String,
): SteamCodeSnapshot {
    val secondsRemaining = 30 - (currentEpochSeconds % 30L).toInt()
    val progress = ((30 - secondsRemaining) / 30f).coerceIn(0f, 1f)

    val codeDisplay = try {
        val secretBytes = SteamSecretCodec.decode(token.sharedSecret)
        try {
            vaultCryptography.generateSteamGuardCode(secretBytes, currentEpochSeconds)
        } finally {
            secretBytes.fill(0)
        }
    } catch (_: IllegalArgumentException) {
        errorCodeDisplay
    }

    return SteamCodeSnapshot(
        codeDisplay = codeDisplay,
        secondsRemaining = secondsRemaining,
        progress = progress,
        hasSecretError = codeDisplay == errorCodeDisplay,
    )
}
