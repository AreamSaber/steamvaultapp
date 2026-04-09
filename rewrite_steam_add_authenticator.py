import re

with open("app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAddAuthenticatorScreen.kt", "r") as f:
    content = f.read()

# We want to remove all protocol login state variables
content = re.sub(r'    var usernameInput by rememberSaveable \{ mutableStateOf\(""\) \}\n.*    var challengeCodeInput by rememberSaveable \{ mutableStateOf\(""\) \}\n', '', content, flags=re.DOTALL)

# Remove the cancel job in DisposableEffect
content = re.sub(r'        onDispose \{\n            protocolLoginJob\?.cancel\(\)\n            pendingChallengeResponse\?.complete\(SteamProtocolLoginChallengeAnswer\.Cancelled\)\n            pendingChallengeResponse = null\n        \}', '        onDispose { }', content)

# Remove functions
content = re.sub(r'    fun submitChallenge\(.*?\n    }\n', '', content, flags=re.DOTALL)
content = re.sub(r'    suspend fun persistProtocolBindingContext\(.*?\n    }\n', '', content, flags=re.DOTALL)
content = re.sub(r'    fun cancelProtocolLogin\(.*?\n    }\n', '', content, flags=re.DOTALL)
content = re.sub(r'    fun startProtocolLogin\(.*?\n        protocolLoginJob = job\n    }\n', '', content, flags=re.DOTALL)

# Remove the ScreenSectionCard for protocol_title
content = re.sub(r'        item \{\n            ScreenSectionCard\(\n                title = stringResource\(R\.string\.steam_add_authenticator_protocol_title\).*?                \}\n            \}\n        \}\n', '', content, flags=re.DOTALL)

# Remove activeQrChallenge
content = re.sub(r'        activeQrChallenge\?\.let \{ qrChallenge ->\n            item \{\n                ScreenSectionCard\(\n                    title = stringResource\(R\.string\.steam_add_authenticator_protocol_qr_preview_title\).*?                \}\n            \}\n        \}\n', '', content, flags=re.DOTALL)

# Remove pendingChallenge
content = re.sub(r'        pendingChallenge\?\.let \{ challenge ->\n            item \{\n                ScreenSectionCard\(\n                    title = challengeTitle\(challenge\).*?                \}\n            \}\n        \}\n', '', content, flags=re.DOTALL)

# Remove storedBindingContext UI
content = re.sub(r'        storedBindingContext\?\.let \{ bindingContext ->\n            item \{\n                ScreenSectionCard\(\n                    title = stringResource\(R\.string\.steam_add_authenticator_protocol_saved_title\).*?                \}\n            \}\n        \}\n', '', content, flags=re.DOTALL)

# Remove challenge helper functions
content = re.sub(r'@Composable\nprivate fun challengeTitle\(.*?\n}\n', '', content, flags=re.DOTALL)
content = re.sub(r'@Composable\nprivate fun challengeDescription\(.*?\n}\n', '', content, flags=re.DOTALL)
content = re.sub(r'@Composable\nprivate fun challengeSupportingText\(.*?\n}\n', '', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAddAuthenticatorScreen.kt", "w") as f:
    f.write(content)

