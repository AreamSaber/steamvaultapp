package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupRemoteVersion
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object CloudBackupVersioning {
    private const val HISTORY_DIRECTORY_NAME = "_history"
    private val ISO_UTC_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun buildHistoryDirectoryPath(baseRemotePath: String): String {
        val normalizedBasePath = normalizeRemotePath(baseRemotePath)
        val parent = normalizedBasePath.substringBeforeLast('/', "")
        return when {
            parent.isBlank() -> "/$HISTORY_DIRECTORY_NAME"
            else -> "$parent/$HISTORY_DIRECTORY_NAME"
        }
    }

    fun buildVersionedRemotePath(baseRemotePath: String, uploadedAtIsoUtc: String): String {
        val normalizedBasePath = normalizeRemotePath(baseRemotePath)
        val historyDirectory = buildHistoryDirectoryPath(normalizedBasePath)
        val fileName = normalizedBasePath.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
            .takeIf(String::isNotBlank)
            ?.let { ".$it" }
            .orEmpty()
        val safeTimestamp = uploadedAtIsoUtc.replace("-", "").replace(":", "")
        return "$historyDirectory/${stem}--$safeTimestamp$extension"
    }

    fun toRemoteVersion(
        baseRemotePath: String,
        fileEntry: WebDavFileEntry,
    ): CloudBackupRemoteVersion? {
        if (fileEntry.isDirectory) {
            return null
        }

        val normalizedBasePath = normalizeRemotePath(baseRemotePath)
        val expectedHistoryDirectory = buildHistoryDirectoryPath(normalizedBasePath)
        val expectedPrefix = normalizedBasePath.substringAfterLast('/')
            .substringBeforeLast('.', normalizedBasePath.substringAfterLast('/')) + "--"
        val fileName = fileEntry.remotePath.substringAfterLast('/')
        val fileParent = fileEntry.remotePath.substringBeforeLast('/', "")

        if (fileParent != expectedHistoryDirectory || !fileName.startsWith(expectedPrefix)) {
            return null
        }

        val uploadedAt = fileEntry.lastModifiedAt ?: parseUploadedAtFromFileName(fileName)
        return CloudBackupRemoteVersion(
            remotePath = normalizeRemotePath(fileEntry.remotePath),
            fileName = fileName,
            uploadedAt = uploadedAt,
        )
    }

    fun sortNewestFirst(versions: List<CloudBackupRemoteVersion>): List<CloudBackupRemoteVersion> {
        return versions.sortedWith(
            compareByDescending<CloudBackupRemoteVersion> { parseIsoUtc(it.uploadedAt) ?: Long.MIN_VALUE }
                .thenByDescending { it.fileName },
        )
    }

    private fun parseUploadedAtFromFileName(fileName: String): String? {
        val timestampCandidate = fileName.substringAfterLast("--", "")
            .substringBeforeLast('.')
            .takeIf(String::isNotBlank)
            ?: return null
        val normalized = timestampCandidate
            .replace(Regex("^(\\d{8})T(\\d{6})Z$"), "$1T$2Z")
        return if (normalized.matches(Regex("\\d{8}T\\d{6}Z"))) {
            "${normalized.substring(0, 4)}-${normalized.substring(4, 6)}-${normalized.substring(6, 8)}T" +
                "${normalized.substring(9, 11)}:${normalized.substring(11, 13)}:${normalized.substring(13, 15)}Z"
        } else {
            null
        }
    }

    private fun parseIsoUtc(value: String?): Long? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            ISO_UTC_FORMAT.parse(value)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun normalizeRemotePath(remotePath: String): String {
        val withoutDuplicateSlash = remotePath.trim().replace("/+".toRegex(), "/")
        return when {
            withoutDuplicateSlash.isBlank() -> "/"
            withoutDuplicateSlash.startsWith("/") -> withoutDuplicateSlash
            else -> "/$withoutDuplicateSlash"
        }
    }
}
