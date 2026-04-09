import re

file_path = "app/src/main/java/com/example/steam_vault_app/feature/password/ChangePasswordScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Update string resources to match the new ones
content = content.replace("R.string.change_password_title", "R.string.change_password_modern_title")
content = content.replace("R.string.change_password_description", "R.string.change_password_modern_body")
content = content.replace("R.string.label_new_master_password", "R.string.change_password_modern_field_new")
content = content.replace("R.string.label_confirm_new_password", "R.string.change_password_modern_field_confirm")
content = content.replace("R.string.change_password_action_idle", "R.string.change_password_modern_action")
content = content.replace("R.string.change_password_action_loading", "R.string.create_password_modern_action_loading") # Reusing loading text

content = content.replace("R.string.change_password_requirements_title", "R.string.create_password_modern_rule_title")
content = content.replace("R.string.change_password_requirements_description", "R.string.create_password_modern_rule_body")

content = content.replace("R.string.create_password_requirement_length", "R.string.create_password_modern_rule_length")
content = content.replace("R.string.create_password_requirement_mixed", "R.string.create_password_modern_rule_mix")
content = content.replace("R.string.create_password_requirement_match", "R.string.create_password_modern_rule_match")

with open(file_path, "w") as f:
    f.write(content)
