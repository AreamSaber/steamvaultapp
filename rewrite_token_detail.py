import re

with open("app/src/main/java/com/example/steam_vault_app/feature/tokens/TokenDetailScreen.kt", "r") as f:
    content = f.read()

# Replace LinearProgressIndicator with CircularProgressIndicator, and change layout
# Wait, it's easier to just overwrite the file or use a precise regex. I'll just write a script to completely replace the LazyColumn content.
