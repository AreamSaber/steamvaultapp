package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.domain.model.SteamGuardDataRecord
import org.json.JSONArray
import org.json.JSONObject

data class SteamGuardDataProfile(
    val records: List<SteamGuardDataRecord>,
)

object SteamGuardDataProfileCodec {
    private const val CURRENT_VERSION = 1

    fun encode(profile: SteamGuardDataProfile): String {
        val recordsJson = JSONArray()
        profile.records.forEach { record ->
            recordsJson.put(
                JSONObject()
                    .put("account_name", record.accountName ?: "")
                    .put("steam_id", record.steamId ?: "")
                    .put("guard_data", record.guardData)
                    .put("updated_at", record.updatedAt),
            )
        }

        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("records", recordsJson)
            .toString()
    }

    fun decode(rawProfile: String): SteamGuardDataProfile {
        val json = JSONObject(rawProfile)
        val version = json.optInt("version", 1)
        require(version in 1..CURRENT_VERSION) {
            "Unsupported Steam guard data profile version: $version"
        }

        val records = buildList {
            val recordsJson = json.optJSONArray("records") ?: JSONArray()
            for (index in 0 until recordsJson.length()) {
                val recordJson = recordsJson.getJSONObject(index)
                add(
                    SteamGuardDataRecord(
                        accountName = recordJson.optString("account_name").takeIf { it.isNotBlank() },
                        steamId = recordJson.optString("steam_id").takeIf { it.isNotBlank() },
                        guardData = recordJson.getString("guard_data"),
                        updatedAt = recordJson.getString("updated_at"),
                    ),
                )
            }
        }

        return SteamGuardDataProfile(records = records)
    }
}
