package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamWebLoginAttemptTimelineSummaryFactoryTest {
    @Test
    fun from_failedAttempt_extractsSubmissionOrderAndFailureClosure() {
        val summary = SteamWebLoginAttemptTimelineSummaryFactory.from(
            SteamWebLoginAttemptRecord(
                attemptNumber = 2,
                result = SteamWebLoginTransactionResult.FAILURE_DETECTED,
                finalStage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                timeline = listOf(
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                        stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
                        stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.STAGE_ADVANCED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.EMAIL_CODE_SUBMITTED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.FAILURE_DETECTED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                ),
            ),
        )

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, summary.startedStage)
        assertEquals(
            listOf(
                SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                SteamWebLoginStage.EMAIL_CODE_REQUIRED,
            ),
            summary.submittedStages,
        )
        assertEquals(
            listOf(SteamWebLoginStage.EMAIL_CODE_REQUIRED),
            summary.advancedStages,
        )
        assertEquals(
            SteamWebLoginTransactionEventType.FAILURE_DETECTED,
            summary.terminalEventType,
        )
        assertEquals(SteamWebLoginAttemptClosure.FAILURE, summary.closure)
        assertEquals(
            SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
            summary.failureReason,
        )
    }

    @Test
    fun from_savedAttempt_extractsSavedClosure() {
        val summary = SteamWebLoginAttemptTimelineSummaryFactory.from(
            SteamWebLoginAttemptRecord(
                attemptNumber = 1,
                result = SteamWebLoginTransactionResult.SESSION_SAVED,
                finalStage = SteamWebLoginStage.SESSION_READY,
                timeline = listOf(
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                        stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
                        stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.SESSION_CAPTURED,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.SESSION_SAVED,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                ),
            ),
        )

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, summary.startedStage)
        assertEquals(
            listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            summary.submittedStages,
        )
        assertEquals(emptyList<SteamWebLoginStage>(), summary.advancedStages)
        assertEquals(
            SteamWebLoginTransactionEventType.SESSION_SAVED,
            summary.terminalEventType,
        )
        assertEquals(SteamWebLoginStage.SESSION_READY, summary.terminalStage)
        assertEquals(SteamWebLoginAttemptClosure.SESSION_SAVED, summary.closure)
    }
}
