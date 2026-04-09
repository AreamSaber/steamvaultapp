package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupStatus
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.model.CloudBackupSyncState
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBackupProfileCodecTest {
    @Test
    fun webDavConfiguration_normalizedTrimsServerAndPath() {
        val configuration = WebDavBackupConfiguration(
            serverUrl = " https://dav.jianguoyun.com/dav/ ",
            username = " demo@example.com ",
            appPassword = " secret-token ",
            remotePath = " SteamVault//backup.json ",
        ).normalized()

        assertEquals("https://dav.jianguoyun.com/dav", configuration.serverUrl)
        assertEquals("demo@example.com", configuration.username)
        assertEquals("secret-token", configuration.appPassword)
        assertEquals("/SteamVault/backup.json", configuration.remotePath)
    }

    @Test
    fun encodeThenDecode_roundTripsCloudBackupProfile() {
        val profile = CloudBackupProfile(
            configuration = WebDavBackupConfiguration(
                serverUrl = "https://dav.jianguoyun.com/dav",
                username = "demo@example.com",
                appPassword = "secret-token",
                remotePath = "/SteamVault/backup.json",
            ),
            status = CloudBackupStatus(
                syncState = CloudBackupSyncState.SUCCESS,
                accountLabel = "demo@example.com",
                remotePath = "/SteamVault/backup.json",
                lastUploadAt = "2026-04-07T15:00:00Z",
                lastDownloadAt = "2026-04-07T15:05:00Z",
                lastErrorMessage = null,
                autoBackupState = CloudBackupAutoBackupState.RETRY_SCHEDULED,
                autoBackupReason = CloudBackupAutoBackupReason.CONFIGURATION_CHANGED,
                autoBackupUpdatedAt = "2026-04-07T15:06:00Z",
                autoBackupNextRunAt = "2026-04-07T15:06:30Z",
                autoBackupFailureCount = 2,
            ),
        )

        val decoded = CloudBackupProfileCodec.decode(CloudBackupProfileCodec.encode(profile))

        assertEquals(profile, decoded)
    }
}
