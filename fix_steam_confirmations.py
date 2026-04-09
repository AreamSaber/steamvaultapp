import re

with open("app/src/main/java/com/example/steam_vault_app/feature/steamconfirmations/SteamConfirmationsScreen.kt", "r") as f:
    content = f.read()

# Replace session references with snapshot.session
content = re.sub(r'viewModel\.respondToConfirmation\(\n                                    token = token,\n                                    session = session,\n                                    confirmation = confirmation,\n                                    accept = (true|false),\n                                \)', r'viewModel.respondToConfirmation(\n                                    token = token,\n                                    session = snapshot.session,\n                                    confirmation = confirmation,\n                                    accept = \1,\n                                )', content)

with open("app/src/main/java/com/example/steam_vault_app/feature/steamconfirmations/SteamConfirmationsScreen.kt", "w") as f:
    f.write(content)
