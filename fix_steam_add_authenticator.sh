#!/bin/bash
# Remove steamProtocolLoginOrchestrator parameter
sed -i '/steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,/d' app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAddAuthenticatorScreen.kt
