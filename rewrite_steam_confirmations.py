import re

with open("app/src/main/java/com/example/steam_vault_app/feature/steamconfirmations/SteamConfirmationsScreen.kt", "r") as f:
    content = f.read()

# I will just write a new file content and replace it entirely to be safe
