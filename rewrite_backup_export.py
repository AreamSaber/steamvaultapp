import re

file_path = "app/src/main/java/com/example/steam_vault_app/feature/backup/BackupExportScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Update the strings mapping in the file
content = content.replace("R.string.backup_export_modern_title", "R.string.backup_export_title")
content = content.replace("R.string.backup_export_modern_body", "R.string.backup_export_description")
content = content.replace("R.string.backup_export_modern_card_title", "R.string.backup_export_caution_title")
content = content.replace("R.string.backup_export_modern_card_body", "R.string.backup_export_caution_body")
content = content.replace("R.string.backup_export_modern_caution", "R.string.backup_export_caution_note")
content = content.replace("R.string.backup_export_modern_action_loading", "R.string.backup_export_action_loading")
content = content.replace("R.string.backup_export_modern_action", "R.string.backup_export_action")

with open(file_path, "w") as f:
    f.write(content)
