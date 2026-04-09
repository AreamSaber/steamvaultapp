package com.example.steam_vault_app.feature.importtoken

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SteamExternalBrowserLoginReturn(
    val uri: Uri,
    val receivedAt: String,
)

object SteamExternalBrowserLoginManager {
    private const val callbackScheme = "steamvaultapp"
    private const val callbackHost = "steam-login"
    private const val callbackPath = "add-authenticator"
    private const val steamStoreLoginUrl = "https://store.steampowered.com/login/"

    val callbackUri: Uri = Uri.Builder()
        .scheme(callbackScheme)
        .authority(callbackHost)
        .appendPath(callbackPath)
        .build()

    val loginUri: Uri = Uri.parse(steamStoreLoginUrl)
        .buildUpon()
        .appendQueryParameter("redir", callbackUri.toString())
        .appendQueryParameter("redir_ssl", "1")
        .build()

    private val mutableBrowserReturn = MutableStateFlow<SteamExternalBrowserLoginReturn?>(null)
    val browserReturn: StateFlow<SteamExternalBrowserLoginReturn?> =
        mutableBrowserReturn.asStateFlow()

    fun openLogin(context: Context) {
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, loginUri)
        }.getOrElse {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, loginUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
        }
    }

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (!isCallbackUri(data)) {
            return
        }
        mutableBrowserReturn.value = SteamExternalBrowserLoginReturn(
            uri = data,
            receivedAt = Instant.now().toString(),
        )
    }

    fun clearReturn() {
        mutableBrowserReturn.value = null
    }

    private fun isCallbackUri(uri: Uri): Boolean {
        return uri.scheme == callbackScheme &&
            uri.host == callbackHost &&
            uri.pathSegments.firstOrNull() == callbackPath
    }
}
