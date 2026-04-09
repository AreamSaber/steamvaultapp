package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamWebLoginTransactionReducerTest {
    @Test
    fun markAssistSubmitted_startsTransactionAndAppendsSubmissionEvent() {
        val transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            ),
            stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        )

        assertEquals(1, transaction.attemptNumber)
        assertEquals(SteamWebLoginTransactionState.WAITING_RESULT, transaction.state)
        assertNull(transaction.result)
        assertEquals(
            listOf(
                SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
            ),
            transaction.events.map { it.type },
        )
    }

    @Test
    fun observePage_withPendingCredentialsAndSameStage_keepsPendingResult() {
        val transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(),
            stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        )

        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = transaction,
            observation = SteamWebLoginObservation(
                currentUrl = SteamWebLoginSessionCapture.loginUrl,
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Sign In",
                pageTextSnippet = "Use your account name and password to sign in.",
            ),
        )

        assertEquals(SteamWebLoginProgressKind.PENDING_RESULT, update.progress.kind)
        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, update.progress.stage)
        assertEquals(SteamWebLoginTransactionState.WAITING_RESULT, update.transaction.state)
        assertEquals(
            SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
            update.transaction.pendingAssistStage,
        )
    }

    @Test
    fun observePage_withCredentialAdvanceToEmailCode_clearsPendingStage() {
        val transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(),
            stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        )

        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = transaction,
            observation = SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=&checkemail=1",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Steam Guard",
                pageTextSnippet = "Enter the Steam Guard code we sent to your email.",
            ),
        )

        assertEquals(SteamWebLoginProgressKind.ADVANCED, update.progress.kind)
        assertEquals(SteamWebLoginStage.EMAIL_CODE_REQUIRED, update.progress.stage)
        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, update.progress.submittedStage)
        assertNull(update.transaction.pendingAssistStage)
        assertEquals(SteamWebLoginTransactionState.ACTIVE, update.transaction.state)
        assertEquals(
            listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
            update.transaction.completedStages,
        )
        assertEquals(
            SteamWebLoginTransactionEventType.STAGE_ADVANCED,
            update.transaction.events.last().type,
        )
    }

    @Test
    fun observePage_withInvalidEmailCode_returnsFailureAndClearsPendingStage() {
        val transaction = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(),
            stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
        )

        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = transaction,
            observation = SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/login/home/?goto=&checkemail=1",
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Steam Guard",
                pageTextSnippet = "The code you entered is invalid. Check your email and try again.",
            ),
        )

        assertEquals(SteamWebLoginProgressKind.FAILURE, update.progress.kind)
        assertEquals(
            SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
            update.progress.failureReason,
        )
        assertNull(update.transaction.pendingAssistStage)
        assertEquals(SteamWebLoginTransactionState.FAILED, update.transaction.state)
        assertEquals(
            SteamWebLoginTransactionResult.FAILURE_DETECTED,
            update.transaction.result,
        )
        assertEquals(
            SteamWebLoginTransactionEventType.FAILURE_DETECTED,
            update.transaction.events.last().type,
        )
    }

    @Test
    fun observePage_withDuplicateCapture_marksDuplicateSession() {
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token",
            currentUrl = "https://steamcommunity.com/id/demo/",
        ) ?: error("Expected capture")
        val transaction = SteamWebLoginTransaction(
            lastHandledCaptureSignature = capture.signature,
        )

        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = transaction,
            observation = SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/id/demo/",
                cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token",
                pageTitle = "Steam Community",
                pageTextSnippet = "Welcome back",
            ),
        )

        assertEquals(SteamWebLoginProgressKind.DUPLICATE_SESSION, update.progress.kind)
        assertEquals(SteamWebLoginStage.SESSION_READY, update.progress.stage)
        assertNull(update.transaction.pendingAssistStage)
        assertEquals(SteamWebLoginTransactionState.SESSION_CAPTURED, update.transaction.state)
        assertEquals(
            SteamWebLoginTransactionResult.DUPLICATE_SESSION,
            update.transaction.result,
        )
        assertEquals(
            listOf(SteamWebLoginStage.SESSION_READY),
            update.transaction.completedStages,
        )
        assertEquals(
            SteamWebLoginTransactionEventType.SESSION_CAPTURED,
            update.transaction.events.last().type,
        )
    }

    @Test
    fun observePage_withRepeatedDuplicateCapture_doesNotAppendDuplicateEvent() {
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token",
            currentUrl = "https://steamcommunity.com/id/demo/",
        ) ?: error("Expected capture")
        val transaction = SteamWebLoginTransaction(
            lastHandledCaptureSignature = capture.signature,
            state = SteamWebLoginTransactionState.SESSION_CAPTURED,
            events = listOf(
                SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.SESSION_CAPTURED,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
            ),
        )

        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = transaction,
            observation = SteamWebLoginObservation(
                currentUrl = "https://steamcommunity.com/id/demo/",
                cookieHeader = "sessionid=abc123; steamLoginSecure=76561198000000000%7C%7Csecure-token",
                pageTitle = "Steam Community",
                pageTextSnippet = "Welcome back",
            ),
        )

        assertEquals(1, update.transaction.events.size)
    }

    @Test
    fun markCapturePersisted_marksTransactionSaved() {
        val transaction = SteamWebLoginTransactionReducer.markCapturePersisted(
            SteamWebLoginTransaction(
                state = SteamWebLoginTransactionState.SESSION_CAPTURED,
                events = listOf(
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.SESSION_CAPTURED,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                ),
            ),
        )

        assertEquals(SteamWebLoginTransactionState.SAVED, transaction.state)
        assertEquals(
            SteamWebLoginTransactionResult.SESSION_SAVED,
            transaction.result,
        )
        assertEquals(
            SteamWebLoginTransactionEventType.SESSION_SAVED,
            transaction.events.last().type,
        )
    }

    @Test
    fun markAssistSubmitted_afterClosedAttempt_archivesPreviousAttemptAndStartsNewOne() {
        val retried = SteamWebLoginTransactionReducer.markAssistSubmitted(
            transaction = SteamWebLoginTransaction(
                attemptNumber = 1,
                analysis = SteamWebLoginAnalysis(
                    stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                    failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                ),
                completedStages = listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
                state = SteamWebLoginTransactionState.FAILED,
                result = SteamWebLoginTransactionResult.FAILURE_DETECTED,
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
                        type = SteamWebLoginTransactionEventType.FAILURE_DETECTED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                ),
            ),
            stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        )

        assertEquals(2, retried.attemptNumber)
        assertNull(retried.result)
        assertEquals(1, retried.history.size)
        assertEquals(1, retried.history.single().attemptNumber)
        assertEquals(
            SteamWebLoginTransactionResult.FAILURE_DETECTED,
            retried.history.single().result,
        )
        assertEquals(
            SteamWebLoginAttemptClosure.FAILURE,
            retried.history.single().summary.closure,
        )
        assertEquals(
            listOf(
                SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
            ),
            retried.history.single().summary.submittedStages,
        )
        assertEquals(
            listOf(
                SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
                SteamWebLoginTransactionEventType.FAILURE_DETECTED,
            ),
            retried.history.single().timeline.map { it.type },
        )
        assertEquals(
            listOf(
                SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED,
            ),
            retried.events.map { it.type },
        )
    }

    @Test
    fun observePage_afterClosedAttempt_archivesPreviousAttemptAndStartsNewOne() {
        val update = SteamWebLoginTransactionReducer.observePage(
            transaction = SteamWebLoginTransaction(
                attemptNumber = 1,
                analysis = SteamWebLoginAnalysis(
                    stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                    failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                ),
                state = SteamWebLoginTransactionState.FAILED,
                result = SteamWebLoginTransactionResult.FAILURE_DETECTED,
                events = listOf(
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                        stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.FAILURE_DETECTED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                ),
            ),
            observation = SteamWebLoginObservation(
                currentUrl = SteamWebLoginSessionCapture.loginUrl,
                cookieHeader = "timezoneOffset=28800,0",
                pageTitle = "Sign In",
                pageTextSnippet = "Use your account name and password to sign in.",
            ),
        )

        assertEquals(2, update.transaction.attemptNumber)
        assertEquals(1, update.transaction.history.size)
        assertEquals(1, update.transaction.history.single().attemptNumber)
        assertEquals(
            SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
            update.transaction.events.first().type,
        )
    }

    @Test
    fun resetForReload_archivesClosedAttemptAndPreservesHistory() {
        val reset = SteamWebLoginTransactionReducer.resetForReload(
            SteamWebLoginTransaction(
                attemptNumber = 2,
                state = SteamWebLoginTransactionState.FAILED,
                result = SteamWebLoginTransactionResult.FAILURE_DETECTED,
                history = listOf(
                    SteamWebLoginAttemptRecord(
                        attemptNumber = 1,
                        result = SteamWebLoginTransactionResult.SESSION_SAVED,
                        finalStage = SteamWebLoginStage.SESSION_READY,
                        completedStages = listOf(SteamWebLoginStage.SESSION_READY),
                        eventCount = 4,
                        timeline = listOf(
                            SteamWebLoginTransactionEvent(
                                type = SteamWebLoginTransactionEventType.SESSION_SAVED,
                                stage = SteamWebLoginStage.SESSION_READY,
                            ),
                        ),
                    ),
                ),
                events = listOf(
                    SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.FAILURE_DETECTED,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                ),
            ),
        )

        assertEquals(2, reset.attemptNumber)
        assertEquals(SteamWebLoginTransactionState.IDLE, reset.state)
        assertNull(reset.result)
        assertEquals(2, reset.history.size)
        assertEquals(2, reset.history.last().attemptNumber)
        assertEquals(
            SteamWebLoginTransactionResult.FAILURE_DETECTED,
            reset.history.last().result,
        )
        assertEquals(
            SteamWebLoginAttemptClosure.FAILURE,
            reset.history.last().summary.closure,
        )
        assertEquals(1, reset.history.last().timeline.size)
        assertEquals(emptyList<SteamWebLoginTransactionEvent>(), reset.events)
    }
}
