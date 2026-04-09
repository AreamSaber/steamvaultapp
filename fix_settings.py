import re

file_path = "app/src/main/java/com/example/steam_vault_app/feature/settings/SettingsScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix the remaining Unresolved references for SettingsScreen by importing them properly
content = content.replace("import androidx.compose.material.icons.filled.Sync\nimport androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight\nimport androidx.compose.material.icons.filled.KeyboardArrowDown\nimport androidx.compose.material.icons.filled.Person\nimport androidx.compose.material.icons.filled.Lock\nimport androidx.compose.material.icons.filled.Refresh\nimport androidx.compose.material.icons.filled.Cloud\n", "")

new_imports = """import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon"""

content = content.replace("import androidx.compose.material3.Icon", new_imports)

# Fix CloudSync that was left out
content = content.replace("androidx.compose.material.icons.filled.CloudSync", "androidx.compose.material.icons.filled.Sync")

with open(file_path, "w") as f:
    f.write(content)
