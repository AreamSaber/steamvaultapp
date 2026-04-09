import os

# Update ScreenSectionCard.kt
file_path = "app/src/main/java/com/example/steam_vault_app/ui/common/ScreenSectionCard.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace Card shape and borders to match the design
content = content.replace("shape = MaterialTheme.shapes.large,", "shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),")
content = content.replace("verticalArrangement = Arrangement.spacedBy(14.dp),", "verticalArrangement = Arrangement.spacedBy(16.dp),")

with open(file_path, "w") as f:
    f.write(content)

# Update VaultComponents.kt
file_path2 = "app/src/main/java/com/example/steam_vault_app/ui/common/VaultComponents.kt"
with open(file_path2, "r") as f:
    content2 = f.read()

# Replace button shape and colors
content2 = content2.replace("shape = CircleShape,", "shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),")
# Make TextField filled with no indicator or specific indicator
# Actually, I'll just rewrite VaultComponents.kt's button and textfield.
