package com.example.steam_vault_app.feature.steamqrlogin

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SteamQrLoginLinkManager {
    private val mutablePendingChallengeUrl = MutableStateFlow<String?>(null)
    val pendingChallengeUrl: StateFlow<String?> = mutablePendingChallengeUrl.asStateFlow()

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val challengeUrl = extractChallengeUrl(data) ?: return
        mutablePendingChallengeUrl.value = challengeUrl
    }

    fun clearPendingChallengeUrl() {
        mutablePendingChallengeUrl.value = null
    }

    private fun extractChallengeUrl(uri: Uri): String? {
        if (
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("s.team", ignoreCase = true) &&
            uri.path?.startsWith("/q") == true
        ) {
            return uri.toString()
        }

        if (
            uri.scheme.equals("steamvaultapp", ignoreCase = true) &&
            uri.host.equals("steam-login", ignoreCase = true) &&
            uri.path.equals("/qr-approve", ignoreCase = true)
        ) {
            return uri.getQueryParameter("url")?.trim()?.takeIf { it.isNotEmpty() }
        }

        return null
    }
}
