package com.example.steam_vault_app.domain.model

data class AppSecuritySettings(
    val secureScreensEnabled: Boolean = true,
    val biometricQuickUnlockEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeoutOption = AutoLockTimeoutOption.ONE_MINUTE,
)

enum class AutoLockTimeoutOption(
    val preferenceValue: String,
    val timeoutMillis: Long?,
) {
    DISABLED("disabled", null),
    IMMEDIATELY("immediately", 0L),
    THIRTY_SECONDS("thirty_seconds", 30_000L),
    ONE_MINUTE("one_minute", 60_000L),
    FIVE_MINUTES("five_minutes", 5 * 60_000L),
    ;

    companion object {
        val default: AutoLockTimeoutOption = ONE_MINUTE

        fun fromPreferenceValue(value: String?): AutoLockTimeoutOption {
            return entries.firstOrNull { it.preferenceValue == value } ?: default
        }
    }
}
