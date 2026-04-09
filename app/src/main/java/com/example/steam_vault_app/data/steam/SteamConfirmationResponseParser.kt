package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamConfirmation
import org.json.JSONArray
import org.json.JSONObject

internal object SteamConfirmationResponseParser {
    fun parseConfirmationList(
        responseBody: String,
        defaultFailureMessage: String,
    ): List<SteamConfirmation> {
        val root = parseJsonObject(responseBody, defaultFailureMessage)
        ensureSuccess(root, defaultFailureMessage)
        val confirmations = root.optJSONArray("conf") ?: JSONArray()
        val parsedItems = mutableListOf<SteamConfirmation>()
        for (index in 0 until confirmations.length()) {
            val item = confirmations.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) {
                continue
            }
            val nonce = item.optString("nonce")
            if (nonce.isBlank()) {
                continue
            }
            val headline = item.optString("headline").ifBlank {
                item.optString("type_name").ifBlank { id }
            }
            parsedItems += SteamConfirmation(
                id = id,
                nonce = nonce,
                typeCode = item.optInt("type").takeIf { item.has("type") },
                headline = headline,
                summary = parseSummary(item.optJSONArray("summary")),
                warn = item.optString("warn").takeIf { it.isNotBlank() },
                creatorId = item.optString("creator_id").takeIf { it.isNotBlank() },
                creationTimeEpochSeconds = item.optLong("creation_time")
                    .takeIf { item.has("creation_time") },
                isMulti = readBooleanFlag(item, "multi"),
                iconUrl = item.optString("icon").takeIf { it.isNotBlank() },
            )
        }
        return parsedItems
    }

    fun ensureActionSucceeded(
        responseBody: String,
        defaultFailureMessage: String,
    ) {
        val root = parseJsonObject(responseBody, defaultFailureMessage)
        ensureSuccess(root, defaultFailureMessage)
    }

    private fun parseJsonObject(
        responseBody: String,
        defaultFailureMessage: String,
    ): JSONObject {
        return try {
            JSONObject(responseBody)
        } catch (_: Exception) {
            throw IllegalStateException(defaultFailureMessage)
        }
    }

    private fun ensureSuccess(
        root: JSONObject,
        defaultFailureMessage: String,
    ) {
        if (readBooleanFlag(root, "needauth")) {
            throw IllegalStateException(
                root.optString("message").takeIf { it.isNotBlank() } ?: defaultFailureMessage,
            )
        }
        if (readBooleanFlag(root, "success")) {
            return
        }
        throw IllegalStateException(
            root.optString("message").takeIf { it.isNotBlank() } ?: defaultFailureMessage,
        )
    }

    private fun readBooleanFlag(
        root: JSONObject,
        key: String,
    ): Boolean {
        val rawValue = root.opt(key)
        return when (rawValue) {
            is Boolean -> rawValue
            is Number -> rawValue.toInt() != 0
            is String -> {
                rawValue.equals("true", ignoreCase = true) ||
                    rawValue.equals("yes", ignoreCase = true) ||
                    rawValue == "1"
            }
            else -> false
        }
    }

    private fun parseSummary(summaryArray: JSONArray?): List<String> {
        if (summaryArray == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until summaryArray.length()) {
                when (val item = summaryArray.opt(index)) {
                    is String -> if (item.isNotBlank()) add(item)
                    is JSONObject -> {
                        val value = item.optString("value").ifBlank {
                            item.optString("text")
                        }
                        if (value.isNotBlank()) {
                            add(value)
                        }
                    }
                }
            }
        }
    }
}
