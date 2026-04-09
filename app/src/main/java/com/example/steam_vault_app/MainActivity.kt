package com.example.steam_vault_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.steam_vault_app.feature.importtoken.SteamExternalBrowserLoginManager
import com.example.steam_vault_app.feature.steamqrlogin.SteamQrLoginLinkManager
import com.example.steam_vault_app.platform.security.AndroidWindowSecurityController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SteamExternalBrowserLoginManager.handleIntent(intent)
        SteamQrLoginLinkManager.handleIntent(intent)

        val windowSecurityController = AndroidWindowSecurityController(this)

        setContent {
            SteamVaultApp(windowSecurityController = windowSecurityController)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SteamExternalBrowserLoginManager.handleIntent(intent)
        SteamQrLoginLinkManager.handleIntent(intent)
    }
}
