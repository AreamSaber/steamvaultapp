package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavRequestFactoryTest {
    @Test
    fun buildAuthorizationHeader_usesBasicAuthWithUtf8Credentials() {
        val header = WebDavRequestFactory.buildAuthorizationHeader(
            WebDavBackupConfiguration(
                serverUrl = "https://dav.jianguoyun.com/dav",
                username = "demo@example.com",
                appPassword = "token-123",
                remotePath = "/Steam Vault/backup.json",
            ),
        )

        assertEquals(
            "Basic ZGVtb0BleGFtcGxlLmNvbTp0b2tlbi0xMjM=",
            header,
        )
    }

    @Test
    fun buildFileAndCollectionUrls_encodePathSegments() {
        val configuration = WebDavBackupConfiguration(
            serverUrl = "https://dav.jianguoyun.com/dav/",
            username = "demo@example.com",
            appPassword = "token-123",
            remotePath = "/Steam Vault/folder one/backup file.json",
        )

        assertEquals(
            "https://dav.jianguoyun.com/dav/Steam%20Vault/folder%20one/backup%20file.json",
            WebDavRequestFactory.buildFileUrl(configuration),
        )
        assertEquals(
            listOf(
                "https://dav.jianguoyun.com/dav/Steam%20Vault",
                "https://dav.jianguoyun.com/dav/Steam%20Vault/folder%20one",
            ),
            WebDavRequestFactory.buildCollectionUrls(configuration),
        )
    }
}
