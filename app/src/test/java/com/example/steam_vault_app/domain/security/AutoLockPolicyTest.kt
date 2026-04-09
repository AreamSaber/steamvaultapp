package com.example.steam_vault_app.domain.security

import com.example.steam_vault_app.domain.model.AutoLockTimeoutOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockPolicyTest {
    @Test
    fun shouldLockOnForegroundResume_returnsFalseWhenTimeoutDisabled() {
        assertFalse(
            AutoLockPolicy.shouldLockOnForegroundResume(
                timeoutMillis = AutoLockTimeoutOption.DISABLED.timeoutMillis,
                backgroundedAtMillis = 10_000L,
                resumedAtMillis = 20_000L,
            ),
        )
    }

    @Test
    fun shouldLockOnForegroundResume_returnsFalseWhenElapsedTimeIsBelowThreshold() {
        assertFalse(
            AutoLockPolicy.shouldLockOnForegroundResume(
                timeoutMillis = AutoLockTimeoutOption.ONE_MINUTE.timeoutMillis,
                backgroundedAtMillis = 100_000L,
                resumedAtMillis = 159_000L,
            ),
        )
    }

    @Test
    fun shouldLockOnForegroundResume_returnsTrueWhenElapsedTimeReachesThreshold() {
        assertTrue(
            AutoLockPolicy.shouldLockOnForegroundResume(
                timeoutMillis = AutoLockTimeoutOption.THIRTY_SECONDS.timeoutMillis,
                backgroundedAtMillis = 5_000L,
                resumedAtMillis = 35_000L,
            ),
        )
    }

    @Test
    fun fromPreferenceValue_returnsDefaultForUnknownValues() {
        assertEquals(
            AutoLockTimeoutOption.default,
            AutoLockTimeoutOption.fromPreferenceValue("unknown"),
        )
    }
}
