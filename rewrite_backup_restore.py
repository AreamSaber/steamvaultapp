import re

file_path = "app/src/main/java/com/example/steam_vault_app/feature/backup/BackupRestoreScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Update the strings mapping in the file
content = content.replace("R.string.backup_restore_modern_title", "R.string.backup_restore_title")
content = content.replace("R.string.backup_restore_modern_body", "R.string.backup_restore_description")
content = content.replace("R.string.backup_restore_modern_card_title", "R.string.backup_restore_caution_title")
content = content.replace("R.string.backup_restore_modern_card_body", "R.string.backup_restore_caution_body")
content = content.replace("R.string.backup_restore_modern_caution", "R.string.backup_restore_caution_note")
content = content.replace("R.string.backup_restore_modern_action_loading", "R.string.backup_restore_action_loading")
content = content.replace("R.string.backup_restore_modern_action", "R.string.backup_restore_action")

with open(file_path, "w") as f:
    f.write(content)
