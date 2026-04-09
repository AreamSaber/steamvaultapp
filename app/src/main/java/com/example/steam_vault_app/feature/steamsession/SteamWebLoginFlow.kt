package com.example.steam_vault_app.feature.steamsession

internal enum class SteamWebLoginNextActionKind {
    OBSERVE_PAGE,
    ENTER_CREDENTIALS,
    ENTER_EMAIL_CODE,
    ENTER_TWO_FACTOR_CODE,
    COMPLETE_ADDITIONAL_CHALLENGE,
    WAIT_FOR_BROWSER_RESULT,
    SESSION_READY,
}

internal data class SteamWebLoginNextAction(
    val kind: SteamWebLoginNextActionKind,
    val stage: SteamWebLoginStage,
    val failureReason: SteamWebLoginFailureReason? = null,
)

internal data class SteamWebLoginFlow(
    val transaction: SteamWebLoginTransaction,
    val progress: SteamWebLoginProgress,
    val nextAction: SteamWebLoginNextAction,
) {
    val stage: SteamWebLoginStage
        get() = transaction.analysis.stage

    val acceptsCredentialsInput: Boolean
        get() = nextAction.kind == SteamWebLoginNextActionKind.ENTER_CREDENTIALS

    val acceptsCodeInput: Boolean
        get() = nextAction.kind == SteamWebLoginNextActionKind.ENTER_EMAIL_CODE ||
            nextAction.kind == SteamWebLoginNextActionKind.ENTER_TWO_FACTOR_CODE

    val assistStage: SteamWebLoginStage?
        get() = when (nextAction.kind) {
            SteamWebLoginNextActionKind.ENTER_CREDENTIALS,
            SteamWebLoginNextActionKind.ENTER_EMAIL_CODE,
            SteamWebLoginNextActionKind.ENTER_TWO_FACTOR_CODE,
            -> nextAction.stage

            else -> null
        }
}

internal object SteamWebLoginFlowFactory {
    fun from(transaction: SteamWebLoginTransaction): SteamWebLoginFlow {
        val progress = transaction.lastProgress
        val nextAction = when (progress.kind) {
            SteamWebLoginProgressKind.SESSION_READY,
            SteamWebLoginProgressKind.DUPLICATE_SESSION,
            -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.SESSION_READY,
                    stage = SteamWebLoginStage.SESSION_READY,
                )
            }

            SteamWebLoginProgressKind.PENDING_RESULT -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.WAIT_FOR_BROWSER_RESULT,
                    stage = progress.stage,
                    failureReason = progress.failureReason,
                )
            }

            SteamWebLoginProgressKind.ADVANCED,
            SteamWebLoginProgressKind.FAILURE,
            SteamWebLoginProgressKind.WAITING,
            -> {
                actionForStage(
                    stage = progress.stage,
                    failureReason = progress.failureReason,
                )
            }
        }

        return SteamWebLoginFlow(
            transaction = transaction,
            progress = progress,
            nextAction = nextAction,
        )
    }

    private fun actionForStage(
        stage: SteamWebLoginStage,
        failureReason: SteamWebLoginFailureReason?,
    ): SteamWebLoginNextAction {
        return when (stage) {
            SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.ENTER_CREDENTIALS,
                    stage = stage,
                    failureReason = failureReason,
                )
            }

            SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.ENTER_EMAIL_CODE,
                    stage = stage,
                    failureReason = failureReason,
                )
            }

            SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.ENTER_TWO_FACTOR_CODE,
                    stage = stage,
                    failureReason = failureReason,
                )
            }

            SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.COMPLETE_ADDITIONAL_CHALLENGE,
                    stage = stage,
                    failureReason = failureReason,
                )
            }

            SteamWebLoginStage.SESSION_READY -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.SESSION_READY,
                    stage = stage,
                )
            }

            SteamWebLoginStage.UNKNOWN -> {
                SteamWebLoginNextAction(
                    kind = SteamWebLoginNextActionKind.OBSERVE_PAGE,
                    stage = stage,
                    failureReason = failureReason,
                )
            }
        }
    }
}
