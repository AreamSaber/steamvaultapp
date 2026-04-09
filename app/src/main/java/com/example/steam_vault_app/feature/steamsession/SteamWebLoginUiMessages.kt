package com.example.steam_vault_app.feature.steamsession

import android.content.Context
import com.example.steam_vault_app.R

internal fun browserWaitingMessage(
    context: Context,
    stage: SteamWebLoginStage,
): String {
    return when (stage) {
        SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
            context.getString(R.string.steam_session_web_login_waiting_credentials)
        }

        SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
            context.getString(R.string.steam_session_web_login_waiting_email_code)
        }

        SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
            context.getString(R.string.steam_session_web_login_waiting_two_factor)
        }

        SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED -> {
            context.getString(R.string.steam_session_web_login_waiting_additional_challenge)
        }

        SteamWebLoginStage.SESSION_READY,
        SteamWebLoginStage.UNKNOWN,
        -> {
            context.getString(R.string.steam_session_web_login_waiting)
        }
    }
}

internal fun browserProgressMessage(
    context: Context,
    progress: SteamWebLoginProgress,
): String {
    return when (progress.kind) {
        SteamWebLoginProgressKind.WAITING,
        SteamWebLoginProgressKind.FAILURE,
        -> {
            browserWaitingMessage(context, progress.stage)
        }

        SteamWebLoginProgressKind.PENDING_RESULT -> {
            when (progress.submittedStage) {
                SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
                    context.getString(
                        R.string.steam_session_web_login_credentials_pending_result,
                    )
                }

                SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                    context.getString(
                        R.string.steam_session_web_login_email_code_pending_result,
                    )
                }

                SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                    context.getString(
                        R.string.steam_session_web_login_two_factor_pending_result,
                    )
                }

                else -> {
                    browserWaitingMessage(context, progress.stage)
                }
            }
        }

        SteamWebLoginProgressKind.ADVANCED -> {
            when (progress.submittedStage) {
                SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
                    when (progress.stage) {
                        SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_credentials_accepted_email_required,
                            )
                        }

                        SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_credentials_accepted_two_factor_required,
                            )
                        }

                        SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_credentials_accepted_additional_challenge,
                            )
                        }

                        else -> {
                            browserWaitingMessage(context, progress.stage)
                        }
                    }
                }

                SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                    when (progress.stage) {
                        SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_email_code_accepted_two_factor_required,
                            )
                        }

                        SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_email_code_accepted_additional_challenge,
                            )
                        }

                        else -> {
                            browserWaitingMessage(context, progress.stage)
                        }
                    }
                }

                SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                    when (progress.stage) {
                        SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED -> {
                            context.getString(
                                R.string.steam_session_web_login_two_factor_accepted_additional_challenge,
                            )
                        }

                        else -> {
                            browserWaitingMessage(context, progress.stage)
                        }
                    }
                }

                else -> {
                    browserWaitingMessage(context, progress.stage)
                }
            }
        }

        SteamWebLoginProgressKind.SESSION_READY -> {
            context.getString(R.string.steam_session_web_login_detected)
        }

        SteamWebLoginProgressKind.DUPLICATE_SESSION -> {
            context.getString(R.string.steam_session_web_login_already_saved)
        }
    }
}

internal fun browserProgressError(
    context: Context,
    progress: SteamWebLoginProgress,
): String? {
    val reason = progress.failureReason ?: return null
    return browserFailureReasonMessage(context, reason)
}

internal fun browserAssistErrorMessage(
    context: Context,
    message: String?,
): String {
    return when (message) {
        "submit_not_ready" -> {
            context.getString(R.string.steam_session_web_login_assist_submit_not_ready)
        }

        "empty_code" -> {
            context.getString(R.string.steam_session_web_login_assist_code_required)
        }

        "empty_account" -> {
            context.getString(R.string.steam_session_web_login_assist_account_required)
        }

        "empty_password" -> {
            context.getString(R.string.steam_session_web_login_assist_password_required)
        }

        "no_account_field" -> {
            context.getString(R.string.steam_session_web_login_assist_no_account_field)
        }

        "no_password_field" -> {
            context.getString(R.string.steam_session_web_login_assist_no_password_field)
        }

        "no_matching_field" -> {
            context.getString(R.string.steam_session_web_login_assist_no_matching_field)
        }

        "invalid_result",
        "invalid_payload",
        "empty_result",
        null,
        -> {
            context.getString(R.string.steam_session_web_login_assist_submit_failed)
        }

        else -> {
            context.getString(
                R.string.steam_session_web_login_assist_submit_failed_with_reason,
                message,
            )
        }
    }
}

internal fun browserConsoleErrorMessage(
    context: Context,
    message: String?,
): String? {
    val normalizedMessage = message?.trim()?.lowercase().orEmpty()
    if (normalizedMessage.isEmpty()) {
        return null
    }
    return when {
        normalizedMessage.contains("failed to get rsa key") ||
            normalizedMessage.contains("cannot start auth session without a valid rsa key") -> {
            context.getString(R.string.steam_session_web_login_failure_rsa_key_unavailable)
        }

        else -> null
    }
}

internal fun browserFailureReasonMessage(
    context: Context,
    reason: SteamWebLoginFailureReason,
): String {
    return when (reason) {
        SteamWebLoginFailureReason.INVALID_CREDENTIALS -> {
            context.getString(R.string.steam_session_web_login_failure_invalid_credentials)
        }

        SteamWebLoginFailureReason.INVALID_EMAIL_CODE -> {
            context.getString(R.string.steam_session_web_login_failure_invalid_email_code)
        }

        SteamWebLoginFailureReason.INVALID_TWO_FACTOR_CODE -> {
            context.getString(R.string.steam_session_web_login_failure_invalid_two_factor_code)
        }

        SteamWebLoginFailureReason.RATE_LIMITED -> {
            context.getString(R.string.steam_session_web_login_failure_rate_limited)
        }

        SteamWebLoginFailureReason.CAPTCHA_REQUIRED -> {
            context.getString(R.string.steam_session_web_login_failure_captcha_required)
        }
    }
}
