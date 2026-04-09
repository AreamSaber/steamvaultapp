package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamWebLoginTransactionSnapshotFactoryTest {
    @Test
    fun from_pendingCredentialsTransaction_marksPendingCheckpoint() {
        val transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            ),
            stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        )

        val snapshot = SteamWebLoginTransactionSnapshotFactory.from(transaction)

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, snapshot.startedStage)
        assertEquals(
            listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            snapshot.submittedStages,
        )
        assertEquals(emptyList<SteamWebLoginStage>(), snapshot.advancedStages)
        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, snapshot.currentStage)
        assertEquals(SteamWebLoginTransactionCheckpoint.PENDING_RESULT, snapshot.checkpoint)
    }

    @Test
    fun fromAdvancedTransaction_marksAdvancedCheckpoint() {
        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
                transaction = SteamWebLoginTransaction(
                    analysis = SteamWebLoginAnalysis(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
                ),
                stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
            ),
            observation = SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=&checkemail=1",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Steam Guard",
                pageTextSnippet = "Enter the Steam Guard code we sent to your email.",
            ),
        )

        val snapshot = SteamWebLoginTransactionSnapshotFactory.from(update.transaction)

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, snapshot.startedStage)
        assertEquals(
            listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            snapshot.submittedStages,
        )
        assertEquals(
            listOf(SteamWebLoginStage.EMAIL_CODE_REQUIRED),
            snapshot.advancedStages,
        )
        assertEquals(SteamWebLoginStage.EMAIL_CODE_REQUIRED, snapshot.currentStage)
        assertEquals(SteamWebLoginTransactionCheckpoint.ADVANCED, snapshot.checkpoint)
    }

    @Test
    fun fromSavedTransaction_marksSavedCheckpoint() {
        val snapshot = SteamWebLoginTransactionSnapshotFactory.from(
            SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.SESSION_READY),
                state = SteamWebLoginTransactionState.SAVED,
                result = SteamWebLoginTransactionResult.SESSION_SAVED,
                events = listOf(
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
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.SESSION_READY,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
            ),
        )

        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, snapshot.startedStage)
        assertEquals(
            listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            snapshot.submittedStages,
        )
        assertEquals(SteamWebLoginStage.SESSION_READY, snapshot.currentStage)
        assertEquals(SteamWebLoginTransactionCheckpoint.SESSION_SAVED, snapshot.checkpoint)
    }
}
