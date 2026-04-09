package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object WebDavRequestFactory {
    fun buildAuthorizationHeader(configuration: WebDavBackupConfiguration): String {
        val credentials = "${configuration.username}:${configuration.appPassword}"
        val encoded = Base64.getEncoder().encodeToString(
            credentials.toByteArray(StandardCharsets.UTF_8),
        )
        return "Basic $encoded"
    }

    fun buildFileUrl(
        configuration: WebDavBackupConfiguration,
        remotePath: String = configuration.remotePath,
    ): String {
        val normalized = configuration.normalized()
        return normalized.serverUrl + encodePath(remotePath)
    }

    fun buildCollectionUrls(
        configuration: WebDavBackupConfiguration,
        remotePath: String = configuration.remotePath,
    ): List<String> {
        val normalized = configuration.normalized()
        val normalizedRemotePath = normalizeRemotePath(remotePath)
        val directoryPath = normalizedRemotePath.substringBeforeLast('/', "")
        if (directoryPath.isBlank()) {
            return emptyList()
        }

        val segments = directoryPath.split('/').filter(String::isNotBlank)
        val urls = mutableListOf<String>()
        var currentPath = ""

        for (segment in segments) {
            currentPath += "/${encodePathSegment(segment)}"
            urls += normalized.serverUrl + currentPath
        }

        return urls
    }

    fun buildDirectoryUrl(
        configuration: WebDavBackupConfiguration,
        remoteDirectoryPath: String,
    ): String {
        val normalized = configuration.normalized()
        return normalized.serverUrl + encodePath(remoteDirectoryPath)
    }

    private fun encodePath(path: String): String {
        val segments = normalizeRemotePath(path).split('/').filter(String::isNotBlank)
        return if (segments.isEmpty()) {
            ""
        } else {
            "/" + segments.joinToString("/") { segment -> encodePathSegment(segment) }
        }
    }

    private fun normalizeRemotePath(remotePath: String): String {
        val withoutDuplicateSlash = remotePath.trim().replace("/+".toRegex(), "/")
        return when {
            withoutDuplicateSlash.isBlank() -> ""
            withoutDuplicateSlash.startsWith("/") -> withoutDuplicateSlash
            else -> "/$withoutDuplicateSlash"
        }
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
    }
}
