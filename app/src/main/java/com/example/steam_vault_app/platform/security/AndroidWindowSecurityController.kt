package com.example.steam_vault_app.platform.security

import android.app.Activity
import android.view.WindowManager

class AndroidWindowSecurityController(
    private val activity: Activity,
) : WindowSecurityController {
    override fun setSensitiveContentProtection(enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
