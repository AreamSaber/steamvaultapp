package com.example.steam_vault_app.data.local

object SteamVaultPreferenceKeys {
    const val SECURITY_PREFS = "steam_vault_security"
    const val VAULT_PREFS = "steam_vault_vault"
    const val CLOUD_BACKUP_PREFS = "steam_vault_cloud_backup"
    const val STEAM_TIME_PREFS = "steam_vault_steam_time"
    const val STEAM_SESSION_PREFS = "steam_vault_steam_session"
    const val STEAM_PROTOCOL_LOGIN_PREFS = "steam_vault_protocol_login"
    const val STEAM_ADD_ACCOUNT_PREFS = "steam_vault_add_account"

    const val KEY_MASTER_PASSWORD_SALT = "master_password_salt"
    const val KEY_MASTER_PASSWORD_HASH = "master_password_hash"
    const val KEY_MASTER_PASSWORD_KDF_NAME = "master_password_kdf_name"
    const val KEY_MASTER_PASSWORD_ITERATIONS = "master_password_iterations"
    const val KEY_MASTER_PASSWORD_MEMORY_KIB = "master_password_memory_kib"
    const val KEY_MASTER_PASSWORD_PARALLELISM = "master_password_parallelism"
    const val KEY_MASTER_PASSWORD_VERSION = "master_password_version"
    const val KEY_VAULT_KEY_SALT = "vault_key_salt"
    const val KEY_WRAPPED_VAULT_KEY_JSON = "wrapped_vault_key_json"
    const val KEY_SECURE_SCREENS_ENABLED = "secure_screens_enabled"
    const val KEY_BIOMETRIC_QUICK_UNLOCK_ENABLED = "biometric_quick_unlock_enabled"
    const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    const val KEY_STEAM_TIME_STATUS = "steam_time_status"
    const val KEY_STEAM_TIME_OFFSET_SECONDS = "steam_time_offset_seconds"
    const val KEY_STEAM_TIME_LAST_SYNC_AT = "steam_time_last_sync_at"
    const val KEY_STEAM_TIME_LAST_SERVER_SECONDS = "steam_time_last_server_seconds"
    const val KEY_STEAM_TIME_LAST_ERROR_MESSAGE = "steam_time_last_error_message"
    const val KEY_ENCRYPTED_STEAM_SESSION_PROFILE_JSON = "encrypted_steam_session_profile_json"
    const val KEY_ENCRYPTED_STEAM_GUARD_DATA_PROFILE_JSON =
        "encrypted_steam_guard_data_profile_json"
    const val KEY_ENCRYPTED_STEAM_AUTHENTICATOR_ENROLLMENT_DRAFT_JSON =
        "encrypted_steam_authenticator_enrollment_draft_json"
    const val KEY_ENCRYPTED_STEAM_AUTHENTICATOR_BINDING_CONTEXT_JSON =
        "encrypted_steam_authenticator_binding_context_json"
    const val KEY_ENCRYPTED_STEAM_AUTHENTICATOR_BINDING_PROGRESS_JSON =
        "encrypted_steam_authenticator_binding_progress_json"

    const val KEY_TOKENS_JSON = "tokens_json"
    const val KEY_ENCRYPTED_VAULT_JSON = "encrypted_vault_json"
    const val KEY_ENCRYPTED_CLOUD_BACKUP_PROFILE_JSON = "encrypted_cloud_backup_profile_json"
    const val KEY_CLOUD_BACKUP_STATUS_JSON = "cloud_backup_status_json"
    const val KEY_BACKGROUND_CLOUD_BACKUP_CONFIGURATION_JSON = "background_cloud_backup_configuration_json"
}
