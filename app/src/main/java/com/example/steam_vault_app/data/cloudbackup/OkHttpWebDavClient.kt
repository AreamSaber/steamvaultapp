package com.example.steam_vault_app.data.cloudbackup

import android.content.Context
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

class OkHttpWebDavClient(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
) : WebDavClient {
    private val messages = Messages.fromContext(context)

    override suspend fun uploadText(
        configuration: WebDavBackupConfiguration,
        payload: String,
        remotePath: String,
    ) = withContext(Dispatchers.IO) {
        val normalized = configuration.normalized()
        val authorizationHeader = WebDavRequestFactory.buildAuthorizationHeader(normalized)

        WebDavRequestFactory.buildCollectionUrls(normalized, remotePath).forEach { collectionUrl ->
            executeWithoutBody(
                Request.Builder()
                    .url(collectionUrl)
                    .header("Authorization", authorizationHeader)
                    .method("MKCOL", null)
                    .build(),
                acceptedCodes = setOf(200, 201, 301, 405),
                errorMessage = messages.createDirectoryFailed,
            )
        }

        executeWithoutBody(
            Request.Builder()
                .url(WebDavRequestFactory.buildFileUrl(normalized, remotePath))
                .header("Authorization", authorizationHeader)
                .header("Content-Type", JSON_MEDIA_TYPE_STRING)
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            acceptedCodes = setOf(200, 201, 204),
            errorMessage = messages.uploadFailed,
        )
    }

    override suspend fun downloadText(
        configuration: WebDavBackupConfiguration,
        remotePath: String,
    ): String = withContext(Dispatchers.IO) {
        val normalized = configuration.normalized()
        val authorizationHeader = WebDavRequestFactory.buildAuthorizationHeader(normalized)

        client.newCall(
            Request.Builder()
                .url(WebDavRequestFactory.buildFileUrl(normalized, remotePath))
                .header("Authorization", authorizationHeader)
                .get()
                .build(),
        ).execute().use { response ->
            when (response.code) {
                200, 206 -> {
                    return@withContext response.body?.string()
                        ?: throw IllegalStateException(messages.emptyBackup)
                }

                404 -> throw IllegalStateException(messages.backupNotFound)

                else -> {
                    val responseBody = response.body?.string().orEmpty()
                    throw IllegalStateException(
                        "${messages.downloadFailed}${errorSuffix(response.code, responseBody)}",
                    )
                }
            }
        }
    }

    override suspend fun listFiles(
        configuration: WebDavBackupConfiguration,
        remoteDirectoryPath: String,
    ): List<WebDavFileEntry> = withContext(Dispatchers.IO) {
        val normalized = configuration.normalized()
        val authorizationHeader = WebDavRequestFactory.buildAuthorizationHeader(normalized)
        val requestBody = PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE)

        client.newCall(
            Request.Builder()
                .url(WebDavRequestFactory.buildDirectoryUrl(normalized, remoteDirectoryPath))
                .header("Authorization", authorizationHeader)
                .header("Depth", "1")
                .method("PROPFIND", requestBody)
                .build(),
        ).execute().use { response ->
            when (response.code) {
                200, 207 -> {
                    val responseBody = response.body?.string().orEmpty()
                    parsePropfindEntries(responseBody, normalized.serverUrl)
                        .filterNot { it.remotePath == normalizeRemotePath(remoteDirectoryPath) }
                }

                404 -> emptyList()

                else -> {
                    val responseBody = response.body?.string().orEmpty()
                    throw IllegalStateException(
                        "${messages.listFailed}${errorSuffix(response.code, responseBody)}",
                    )
                }
            }
        }
    }

    override suspend fun delete(
        configuration: WebDavBackupConfiguration,
        remotePath: String,
    ) = withContext(Dispatchers.IO) {
        val normalized = configuration.normalized()
        val authorizationHeader = WebDavRequestFactory.buildAuthorizationHeader(normalized)

        executeWithoutBody(
            Request.Builder()
                .url(WebDavRequestFactory.buildFileUrl(normalized, remotePath))
                .header("Authorization", authorizationHeader)
                .delete()
                .build(),
            acceptedCodes = setOf(200, 202, 204, 404),
            errorMessage = messages.deleteFailed,
        )
    }

    private fun executeWithoutBody(
        request: Request,
        acceptedCodes: Set<Int>,
        errorMessage: String,
    ) {
        client.newCall(request).execute().use { response ->
            if (response.code !in acceptedCodes) {
                val responseBody = response.body?.string().orEmpty()
                throw IllegalStateException(
                    "$errorMessage${errorSuffix(response.code, responseBody)}",
                )
            }
        }
    }

    private fun parsePropfindEntries(
        responseBody: String,
        serverUrl: String,
    ): List<WebDavFileEntry> {
        if (responseBody.isBlank()) {
            return emptyList()
        }

        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = documentBuilderFactory.newDocumentBuilder()
            .parse(InputSource(StringReader(responseBody)))
        val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
        val entries = mutableListOf<WebDavFileEntry>()

        for (index in 0 until responses.length) {
            val responseElement = responses.item(index) as? Element ?: continue
            val href = responseElement.getElementsByTagNameNS(DAV_NAMESPACE, "href")
                .item(0)
                ?.textContent
                ?.trim()
                .orEmpty()
            val remotePath = extractRemotePath(serverUrl, href) ?: continue
            val lastModifiedAt = parseLastModified(responseElement)
            val isDirectory = responseElement
                .getElementsByTagNameNS(DAV_NAMESPACE, "collection")
                .length > 0

            entries += WebDavFileEntry(
                remotePath = remotePath,
                isDirectory = isDirectory,
                lastModifiedAt = lastModifiedAt,
            )
        }

        return entries
    }

    private fun parseLastModified(responseElement: Element): String? {
        val rawValue = responseElement.getElementsByTagNameNS(DAV_NAMESPACE, "getlastmodified")
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
        if (rawValue.isBlank()) {
            return null
        }

        return try {
            HTTP_DATE_FORMAT.parse(rawValue)?.let { HTTP_DATE_OUTPUT_FORMAT.format(it) }
        } catch (_: ParseException) {
            null
        }
    }

    private fun extractRemotePath(serverUrl: String, href: String): String? {
        if (href.isBlank()) {
            return null
        }

        val basePath = URI(serverUrl).path.trimEnd('/')
        val hrefPath = runCatching {
            val parsed = URI(href)
            if (parsed.scheme == null) href else parsed.path
        }.getOrDefault(href)

        val normalizedHrefPath = hrefPath.trimEnd('/')
        val relativePath = when {
            normalizedHrefPath.startsWith(basePath) -> normalizedHrefPath.removePrefix(basePath)
            else -> normalizedHrefPath
        }

        return normalizeRemotePath(decodePath(relativePath))
    }

    private fun decodePath(path: String): String {
        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) {
            return "/"
        }

        return "/" + segments.joinToString("/") { segment ->
            URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8)
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

    private fun errorSuffix(code: Int, body: String): String {
        return if (body.isBlank()) {
            messages.httpSuffixWithoutBody(code)
        } else {
            messages.httpSuffixWithBody(code, body.take(MAX_ERROR_BODY_LENGTH))
        }
    }

    private data class Messages(
        val createDirectoryFailed: String,
        val uploadFailed: String,
        val emptyBackup: String,
        val backupNotFound: String,
        val downloadFailed: String,
        val listFailed: String,
        val deleteFailed: String,
        val httpSuffixWithoutBody: (Int) -> String,
        val httpSuffixWithBody: (Int, String) -> String,
    ) {
        companion object {
            fun fromContext(context: Context?): Messages {
                if (context == null) {
                    return Messages(
                        createDirectoryFailed = "Unable to create cloud backup directory.",
                        uploadFailed = "Unable to upload encrypted backup.",
                        emptyBackup = "Cloud backup content was empty.",
                        backupNotFound = "No cloud backup file was found.",
                        downloadFailed = "Unable to download encrypted backup.",
                        listFailed = "Unable to list remote backup versions.",
                        deleteFailed = "Unable to clean up old remote backup versions.",
                        httpSuffixWithoutBody = { code -> " (HTTP $code)" },
                        httpSuffixWithBody = { code, body -> " (HTTP $code: $body)" },
                    )
                }
                val appContext = context.applicationContext
                return Messages(
                    createDirectoryFailed = appContext.getString(R.string.webdav_create_directory_failed),
                    uploadFailed = appContext.getString(R.string.webdav_upload_failed),
                    emptyBackup = appContext.getString(R.string.webdav_empty_backup),
                    backupNotFound = appContext.getString(R.string.webdav_backup_not_found),
                    downloadFailed = appContext.getString(R.string.webdav_download_failed),
                    listFailed = appContext.getString(R.string.webdav_list_failed),
                    deleteFailed = appContext.getString(R.string.webdav_delete_failed),
                    httpSuffixWithoutBody = { code ->
                        appContext.getString(R.string.webdav_http_suffix_without_body, code)
                    },
                    httpSuffixWithBody = { code, body ->
                        appContext.getString(R.string.webdav_http_suffix_with_body, code, body)
                    },
                )
            }
        }
    }

    private companion object {
        private const val JSON_MEDIA_TYPE_STRING = "application/json; charset=utf-8"
        private val JSON_MEDIA_TYPE = JSON_MEDIA_TYPE_STRING.toMediaType()
        private const val XML_MEDIA_TYPE_STRING = "application/xml; charset=utf-8"
        private val XML_MEDIA_TYPE = XML_MEDIA_TYPE_STRING.toMediaType()
        private const val MAX_ERROR_BODY_LENGTH = 160
        private const val DAV_NAMESPACE = "DAV:"
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""
        private val HTTP_DATE_FORMAT = SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            Locale.US,
        ).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        private val HTTP_DATE_OUTPUT_FORMAT = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
