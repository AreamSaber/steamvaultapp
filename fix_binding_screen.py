import re

with open("app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAuthenticatorBindingScreen.kt", "r") as f:
    content = f.read()

# Remove the oauthToken UI checks in ChecklistRow
content = re.sub(r'                        ChecklistRow\(\n                            label = stringResource\(\n                                if \(bindingPreparation\.oauthToken != null && bindingPreparation\.webApiKey != null\) \{\n                                    R\.string\.steam_authenticator_binding_check_both_auth_ready\n                                \} else if \(bindingPreparation\.oauthToken != null\) \{\n                                    R\.string\.steam_authenticator_binding_check_oauth_ready\n                                \} else if \(bindingPreparation\.webApiKey != null\) \{\n                                    R\.string\.steam_authenticator_binding_check_web_api_key_ready\n                                \} else \{\n                                    R\.string\.steam_authenticator_binding_check_auth_missing\n                                \},\n                            \),\n                            highlighted = bindingPreparation\.oauthToken != null \|\|\n                                bindingPreparation\.webApiKey != null,\n                        \)', '', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAuthenticatorBindingScreen.kt", "w") as f:
    f.write(content)
