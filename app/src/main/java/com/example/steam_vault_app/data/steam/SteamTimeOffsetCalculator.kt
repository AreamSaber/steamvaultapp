package com.example.steam_vault_app.data.steam

import com.example.steam_vault_app.data.DataMessageCatalog

object SteamTimeOffsetCalculator {
    fun calculateOffsetSeconds(
        serverTimeSeconds: Long,
        deviceTimeSeconds: Long,
    ): Long {
        require(serverTimeSeconds >= 0L) { DataMessageCatalog.steamTimeOffsetNegativeServer() }
        require(deviceTimeSeconds >= 0L) { DataMessageCatalog.steamTimeOffsetNegativeDevice() }
        return serverTimeSeconds - deviceTimeSeconds
    }

    fun applyOffset(
        deviceTimeSeconds: Long,
        offsetSeconds: Long,
    ): Long {
        require(deviceTimeSeconds >= 0L) { DataMessageCatalog.steamTimeOffsetNegativeDevice() }
        return deviceTimeSeconds + offsetSeconds
    }
}
