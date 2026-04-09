import re

with open("app/src/main/res/values/strings_add_account.xml", "r") as f:
    content = f.read()

replacements = {
    r'<string name="import_login_path_title">.*?</string>': r'<string name="import_login_path_title">Steam Login (Recommended)</string>',
    r'<string name="import_login_path_description">.*?</string>': r'<string name="import_login_path_description">Sign in directly to add a new Steam Guard Mobile Authenticator.</string>',
    r'<string name="import_fallback_title">.*?</string>': r'<string name="import_fallback_title">Manual Import</string>',
    r'<string name="import_fallback_description">.*?</string>': r'<string name="import_fallback_description">Import an existing authenticator from a maFile, JSON, or URI.</string>',
    r'<string name="import_fallback_note">.*?</string>': r'<string name="import_fallback_note">Use this option if you already have the authenticator secrets.</string>',
    r'<string name="steam_add_authenticator_title">.*?</string>': r'<string name="steam_add_authenticator_title">Sign In with Steam</string>',
    r'<string name="steam_add_authenticator_overview_title">.*?</string>': r'<string name="steam_add_authenticator_overview_title">Flow Overview</string>',
    r'<string name="steam_add_authenticator_overview_description">.*?</string>': r'<string name="steam_add_authenticator_overview_description">Follow the steps to add a new authenticator.</string>',
    r'<string name="steam_add_authenticator_protocol_title">.*?</string>': r'<string name="steam_add_authenticator_protocol_title">Steam Account</string>',
    r'<string name="steam_add_authenticator_protocol_description">.*?</string>': r'<string name="steam_add_authenticator_protocol_description">Enter your Steam credentials to continue.</string>',
    r'<string name="steam_add_authenticator_protocol_note">.*?</string>': r'<string name="steam_add_authenticator_protocol_note">Your credentials are only sent to official Steam servers.</string>',
}

for pattern, replacement in replacements.items():
    content = re.sub(pattern, replacement, content)

with open("app/src/main/res/values/strings_add_account.xml", "w") as f:
    f.write(content)

