package com.example.steam_vault_app.domain.security

object AutoLockPolicy {
    fun shouldLockOnForegroundResume(
        timeoutMillis: Long?,
        backgroundedAtMillis: Long?,
        resumedAtMillis: Long,
    ): Boolean {
        if (timeoutMillis == null || backgroundedAtMillis == null) {
            return false
        }

        return resumedAtMillis - backgroundedAtMillis >= timeoutMillis
    }
}
