package com.example.steam_vault_app.feature.steamsession

import java.io.Serializable

internal data class SteamWebLoginTransactionSnapshot(
    val startedStage: SteamWebLoginStage = SteamWebLoginStage.UNKNOWN,
    val submittedStages: List<SteamWebLoginStage> = emptyList(),
    val advancedStages: List<SteamWebLoginStage> = emptyList(),
    val currentStage: SteamWebLoginStage = SteamWebLoginStage.UNKNOWN,
    val checkpoint: SteamWebLoginTransactionCheckpoint = SteamWebLoginTransactionCheckpoint.NOT_STARTED,
    val failureReason: SteamWebLoginFailureReason? = null,
) : Serializable

internal enum class SteamWebLoginTransactionCheckpoint {
    NOT_STARTED,
    WAITING_INPUT,
    PENDING_RESULT,
    ADVANCED,
    FAILURE,
    SESSION_CAPTURED,
    SESSION_SAVED,
    SESSION_SAVE_FAILED,
    DUPLICATE_SESSION,
}

internal object SteamWebLoginTransactionSnapshotFactory {
    fun from(transaction: SteamWebLoginTransaction): SteamWebLoginTransactionSnapshot {
        val startedStage = transaction.events
            .firstOrNull { it.type == SteamWebLoginTransactionEventType.TRANSACTION_STARTED }
            ?.stage
            ?: transaction.events.firstOrNull()?.stage
            ?: transaction.analysis.stage

        val submittedStages = transaction.events
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

        val advancedStages = transaction.events
            .filter { it.type == SteamWebLoginTransactionEventType.STAGE_ADVANCED }
            .map { it.stage }
            .distinct()

        return SteamWebLoginTransactionSnapshot(
            startedStage = startedStage,
            submittedStages = submittedStages,
            advancedStages = advancedStages,
            currentStage = currentStageFor(transaction),
            checkpoint = checkpointFor(transaction),
            failureReason = transaction.lastProgress.failureReason,
        )
    }

    private fun currentStageFor(
        transaction: SteamWebLoginTransaction,
    ): SteamWebLoginStage {
        return transaction.analysis.stage.takeUnless { it == SteamWebLoginStage.UNKNOWN }
            ?: transaction.lastProgress.stage
    }

    private fun checkpointFor(
        transaction: SteamWebLoginTransaction,
    ): SteamWebLoginTransactionCheckpoint {
        if (transaction.events.isEmpty()) {
            return SteamWebLoginTransactionCheckpoint.NOT_STARTED
        }
        return when {
            transaction.result == SteamWebLoginTransactionResult.SESSION_SAVED ||
                transaction.state == SteamWebLoginTransactionState.SAVED -> {
                SteamWebLoginTransactionCheckpoint.SESSION_SAVED
            }

            transaction.result == SteamWebLoginTransactionResult.SESSION_SAVE_FAILED -> {
                SteamWebLoginTransactionCheckpoint.SESSION_SAVE_FAILED
            }

            transaction.result == SteamWebLoginTransactionResult.DUPLICATE_SESSION ||
                transaction.lastProgress.kind == SteamWebLoginProgressKind.DUPLICATE_SESSION -> {
                SteamWebLoginTransactionCheckpoint.DUPLICATE_SESSION
            }

            transaction.result == SteamWebLoginTransactionResult.SESSION_CAPTURED ||
                transaction.lastProgress.kind == SteamWebLoginProgressKind.SESSION_READY ||
                transaction.state == SteamWebLoginTransactionState.SESSION_CAPTURED -> {
                SteamWebLoginTransactionCheckpoint.SESSION_CAPTURED
            }

            transaction.result == SteamWebLoginTransactionResult.FAILURE_DETECTED ||
                transaction.lastProgress.kind == SteamWebLoginProgressKind.FAILURE ||
                transaction.state == SteamWebLoginTransactionState.FAILED -> {
                SteamWebLoginTransactionCheckpoint.FAILURE
            }

            transaction.lastProgress.kind == SteamWebLoginProgressKind.ADVANCED -> {
                SteamWebLoginTransactionCheckpoint.ADVANCED
            }

            transaction.lastProgress.kind == SteamWebLoginProgressKind.PENDING_RESULT ||
                transaction.state == SteamWebLoginTransactionState.WAITING_RESULT -> {
                SteamWebLoginTransactionCheckpoint.PENDING_RESULT
            }

            else -> SteamWebLoginTransactionCheckpoint.WAITING_INPUT
        }
    }
}
