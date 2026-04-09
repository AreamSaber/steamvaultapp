package com.example.steam_vault_app.data.cloudbackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudBackupVersioningTest {
    @Test
    fun buildHistoryDirectoryAndVersionedPath_followExpectedLayout() {
        assertEquals(
            "/SteamVault/_history",
            CloudBackupVersioning.buildHistoryDirectoryPath("/SteamVault/backup.json"),
        )
        assertEquals(
            "/SteamVault/_history/backup--20260407T160000Z.json",
            CloudBackupVersioning.buildVersionedRemotePath(
                baseRemotePath = "/SteamVault/backup.json",
                uploadedAtIsoUtc = "2026-04-07T16:00:00Z",
            ),
        )
    }

    @Test
    fun toRemoteVersion_filtersOutNonManagedFiles() {
        assertNull(
            CloudBackupVersioning.toRemoteVersion(
                baseRemotePath = "/SteamVault/backup.json",
                fileEntry = WebDavFileEntry(
                    remotePath = "/SteamVault/_history/notes.txt",
                    isDirectory = false,
                ),
            ),
        )
    }
}
