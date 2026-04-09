package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.DataMessageCatalog
import org.json.JSONObject

object SteamTimeResponseParser {
    fun parseServerTimeSeconds(rawPayload: String): Long {
        val root = JSONObject(rawPayload)
        val nestedValue = root.optJSONObject("response")?.opt("server_time")
        val topLevelValue = root.opt("server_time")
        val serverTime = nestedValue ?: topLevelValue
            ?: throw IllegalArgumentException(DataMessageCatalog.steamTimeResponseMissingServerTime())

        return when (serverTime) {
            is Number -> serverTime.toLong()
            is String -> serverTime.toLongOrNull()
                ?: throw IllegalArgumentException(DataMessageCatalog.steamTimeResponseInvalidServerTime())
            else -> throw IllegalArgumentException(DataMessageCatalog.steamTimeResponseInvalidServerTime())
        }
    }
}
