package com.example.steam_vault_app.data

import android.content.Context
import com.example.steam_vault_app.R

object DataMessageCatalog {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun steamSecretBlank(): String {
        return string(
            resId = R.string.steam_secret_blank,
            fallback = "shared_secret cannot be blank.",
        )
    }

    fun steamSecretInvalid(): String {
        return string(
            resId = R.string.steam_secret_invalid,
            fallback = "shared_secret must be valid Base64 or Base32.",
        )
    }

    fun steamConfirmationNegativeTimestamp(): String {
        return string(
            resId = R.string.steam_confirmation_negative_timestamp,
            fallback = "Confirmation timestamp must be non-negative.",
        )
    }

    fun steamConfirmationBlankTag(): String {
        return string(
            resId = R.string.steam_confirmation_blank_tag,
            fallback = "Confirmation tag cannot be blank.",
        )
    }

    fun steamConfirmationTagTooLong(): String {
        return string(
            resId = R.string.steam_confirmation_tag_too_long,
            fallback = "Confirmation tag must be at most 32 bytes.",
        )
    }

    fun steamSessionProfileUnsupportedVersion(version: Int): String {
        return string(
            resId = R.string.steam_session_profile_unsupported_version,
            fallback = "Unsupported Steam session profile version: $version",
            formatArgs = arrayOf(version),
        )
    }

    fun steamAuthenticatorDraftUnsupportedVersion(version: Int): String {
        return string(
            resId = R.string.steam_authenticator_draft_unsupported_version,
            fallback = "Unsupported Steam authenticator draft version: $version",
            formatArgs = arrayOf(version),
        )
    }

    fun steamAuthenticatorBindingProgressUnsupportedVersion(version: Int): String {
        return string(
            resId = R.string.steam_authenticator_binding_progress_unsupported_version,
            fallback = "Unsupported Steam authenticator binding progress version: $version",
            formatArgs = arrayOf(version),
        )
    }

    fun steamAuthenticatorBindingContextUnsupportedVersion(version: Int): String {
        return string(
            resId = R.string.steam_authenticator_binding_context_unsupported_version,
            fallback = "Unsupported Steam authenticator binding context version: $version",
            formatArgs = arrayOf(version),
        )
    }

    fun steamAuthenticatorBindingContextMissingSession(): String {
        return string(
            resId = R.string.steam_authenticator_binding_context_missing_session,
            fallback = "Steam authenticator binding context is missing session.",
        )
    }

    fun steamAuthenticatorBindingProgressMissingBeginResult(): String {
        return string(
            resId = R.string.steam_authenticator_binding_progress_missing_begin_result,
            fallback = "Steam authenticator binding progress is missing begin_result.",
        )
    }

    fun steamTimeOffsetNegativeServer(): String {
        return string(
            resId = R.string.steam_time_offset_negative_server,
            fallback = "Steam server time cannot be negative.",
        )
    }

    fun steamTimeOffsetNegativeDevice(): String {
        return string(
            resId = R.string.steam_time_offset_negative_device,
            fallback = "Device time cannot be negative.",
        )
    }

    fun steamTimeResponseMissingServerTime(): String {
        return string(
            resId = R.string.steam_time_response_missing_server_time,
            fallback = "Steam time response is missing server_time.",
        )
    }

    fun steamTimeResponseInvalidServerTime(): String {
        return string(
            resId = R.string.steam_time_response_invalid_server_time,
            fallback = "Steam time response contains an invalid server_time.",
        )
    }

    private fun string(
        resId: Int,
        fallback: String,
        formatArgs: Array<out Any> = emptyArray(),
    ): String {
        val context = appContext ?: return fallback
        return context.getString(resId, *formatArgs)
    }
}
