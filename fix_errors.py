import re

# 1. Fix BackupExportScreen.kt
file_path = "app/src/main/java/com/example/steam_vault_app/feature/backup/BackupExportScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("R.string.backup_export_description", "R.string.backup_export_modern_body")
content = content.replace("R.string.backup_export_caution_title", "R.string.backup_export_modern_card_title")
content = content.replace("R.string.backup_export_caution_body", "R.string.backup_export_modern_card_body")
content = content.replace("R.string.backup_export_caution_note", "R.string.backup_export_modern_caution")
content = content.replace("R.string.backup_export_action_loading", "R.string.backup_export_modern_action_loading")
content = content.replace("R.string.backup_export_action", "R.string.backup_export_modern_action")

with open(file_path, "w") as f:
    f.write(content)

# 2. Fix BackupRestoreScreen.kt
file_path = "app/src/main/java/com/example/steam_vault_app/feature/backup/BackupRestoreScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("R.string.backup_restore_description", "R.string.backup_restore_modern_body")
content = content.replace("R.string.backup_restore_caution_title", "R.string.backup_restore_modern_card_title")
content = content.replace("R.string.backup_restore_caution_body", "R.string.backup_restore_modern_card_body")
content = content.replace("R.string.backup_restore_caution_note", "R.string.backup_restore_modern_caution")
content = content.replace("R.string.backup_restore_action_loading", "R.string.backup_restore_modern_action_loading")
content = content.replace("R.string.backup_restore_action", "R.string.backup_restore_modern_action")

with open(file_path, "w") as f:
    f.write(content)

# 3. Fix SettingsScreen.kt (Icons replacement)
file_path = "app/src/main/java/com/example/steam_vault_app/feature/settings/SettingsScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

icon_replacements = {
    "Icons.Default.ChevronRight": "androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight",
    "Icons.Default.Cloud": "androidx.compose.material.icons.filled.Cloud", # Usually there is a cloud, if not we will use Backup
    "Icons.Default.CloudSync": "androidx.compose.material.icons.filled.Sync",
    "Icons.Default.Download": "androidx.compose.material.icons.filled.KeyboardArrowDown",
    "Icons.Default.Fingerprint": "androidx.compose.material.icons.filled.Person",
    "Icons.Default.LockReset": "androidx.compose.material.icons.filled.Lock",
    "Icons.Default.Restore": "androidx.compose.material.icons.filled.Refresh",
    "Icons.Default.Timer": "androidx.compose.material.icons.filled.Lock",
    "Icons.Default.VisibilityOff": "androidx.compose.material.icons.filled.Lock",
    
    "import androidx.compose.material.icons.filled.ChevronRight\n": "",
    "import androidx.compose.material.icons.filled.Cloud\n": "",
    "import androidx.compose.material.icons.filled.CloudSync\n": "",
    "import androidx.compose.material.icons.filled.Download\n": "",
    "import androidx.compose.material.icons.filled.Fingerprint\n": "",
    "import androidx.compose.material.icons.filled.LockReset\n": "",
    "import androidx.compose.material.icons.filled.Restore\n": "",
    "import androidx.compose.material.icons.filled.Sync\n": "import androidx.compose.material.icons.filled.Sync\nimport androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight\nimport androidx.compose.material.icons.filled.KeyboardArrowDown\nimport androidx.compose.material.icons.filled.Person\nimport androidx.compose.material.icons.filled.Lock\nimport androidx.compose.material.icons.filled.Refresh\nimport androidx.compose.material.icons.filled.Cloud\n",
    "import androidx.compose.material.icons.filled.Timer\n": "",
    "import androidx.compose.material.icons.filled.VisibilityOff\n": "",
}

for k, v in icon_replacements.items():
    content = content.replace(k, v)

with open(file_path, "w") as f:
    f.write(content)

# 4. Fix UnlockScreen.kt (Icons replacement)
file_path = "app/src/main/java/com/example/steam_vault_app/feature/unlock/UnlockScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Icons.Default.Fingerprint", "androidx.compose.material.icons.filled.Person")
content = content.replace("import androidx.compose.material.icons.filled.Fingerprint\n", "import androidx.compose.material.icons.filled.Person\n")

with open(file_path, "w") as f:
    f.write(content)
