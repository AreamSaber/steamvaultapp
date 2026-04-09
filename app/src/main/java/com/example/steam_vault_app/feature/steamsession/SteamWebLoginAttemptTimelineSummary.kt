package com.example.steam_vault_app.feature.steamsession

import java.io.Serializable

internal data class SteamWebLoginAttemptTimelineSummary(
    val startedStage: SteamWebLoginStage = SteamWebLoginStage.UNKNOWN,
    val submittedStages: List<SteamWebLoginStage> = emptyList(),
    val advancedStages: List<SteamWebLoginStage> = emptyList(),
    val terminalEventType: SteamWebLoginTransactionEventType? = null,
    val terminalStage: SteamWebLoginStage = SteamWebLoginStage.UNKNOWN,
    val closure: SteamWebLoginAttemptClosure = SteamWebLoginAttemptClosure.ABANDONED,
    val failureReason: SteamWebLoginFailureReason? = null,
) : Serializable

internal enum class SteamWebLoginAttemptClosure {
    ABANDONED,
    FAILURE,
    SESSION_CAPTURED,
    SESSION_SAVED,
    SESSION_SAVE_FAILED,
    DUPLICATE_SESSION,
}

internal object SteamWebLoginAttemptTimelineSummaryFactory {
    fun from(attempt: SteamWebLoginAttemptRecord): SteamWebLoginAttemptTimelineSummary {
        val startedStage = attempt.timeline
            .firstOrNull { it.type == SteamWebLoginTransactionEventType.TRANSACTION_STARTED }
            ?.stage
            ?: attempt.timeline.firstOrNull()?.stage
            ?: attempt.finalStage

        val submittedStages = attempt.timeline
            .mapNotNull { event ->
                when (event.type) {
                    SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
                    SteamWebLoginTransactionEventType.EMAIL_CODE_SUBMITTED,
                    SteamWebLoginTransactionEventType.TWO_FACTOR_SUBMITTED,
                    -> event.stage

                    else -> null
                }
            }
            .distinct()

        val advancedStages = attempt.timeline
            .filter { it.type == SteamWebLoginTransactionEventType.STAGE_ADVANCED }
            .map { it.stage }
            .distinct()

        val terminalEvent = attempt.timeline.lastOrNull()
        return SteamWebLoginAttemptTimelineSummary(
            startedStage = startedStage,
            submittedStages = submittedStages,
            advancedStages = advancedStages,
            terminalEventType = terminalEvent?.type,
            terminalStage = terminalEvent?.stage ?: attempt.finalStage,
            closure = closureFor(attempt.result),
            failureReason = attempt.failureReason ?: terminalEvent?.failureReason,
        )
    }

    private fun closureFor(
        result: SteamWebLoginTransactionResult,
    ): SteamWebLoginAttemptClosure {
        return when (result) {
            SteamWebLoginTransactionResult.FAILURE_DETECTED -> {
                SteamWebLoginAttemptClosure.FAILURE
            }

            SteamWebLoginTransactionResult.SESSION_CAPTURED -> {
                SteamWebLoginAttemptClosure.SESSION_CAPTURED
            }

            SteamWebLoginTransactionResult.SESSION_SAVED -> {
                SteamWebLoginAttemptClosure.SESSION_SAVED
            }

            SteamWebLoginTransactionResult.SESSION_SAVE_FAILED -> {
                SteamWebLoginAttemptClosure.SESSION_SAVE_FAILED
            }

            SteamWebLoginTransactionResult.DUPLICATE_SESSION -> {
                SteamWebLoginAttemptClosure.DUPLICATE_SESSION
            }
        }
    }
}
