package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamWebLoginFlowFactoryTest {
    @Test
    fun from_withWaitingCredentials_requestsCredentialEntry() {
        val flow = SteamWebLoginFlowFactory.from(
            SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.WAITING,
                    stage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                ),
            ),
        )

        assertEquals(SteamWebLoginNextActionKind.ENTER_CREDENTIALS, flow.nextAction.kind)
        assertTrue(flow.acceptsCredentialsInput)
        assertFalse(flow.acceptsCodeInput)
        assertEquals(SteamWebLoginStage.WAITING_FOR_CREDENTIALS, flow.assistStage)
    }

    @Test
    fun from_withPendingEmailCode_waitsForBrowserResult() {
        val flow = SteamWebLoginFlowFactory.from(
            SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.EMAIL_CODE_REQUIRED),
                pendingAssistStage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.PENDING_RESULT,
                    stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                    submittedStage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                ),
            ),
        )

        assertEquals(SteamWebLoginNextActionKind.WAIT_FOR_BROWSER_RESULT, flow.nextAction.kind)
        assertFalse(flow.acceptsCredentialsInput)
        assertFalse(flow.acceptsCodeInput)
    }

    @Test
    fun from_withInvalidTwoFactor_allowsRetryOnTwoFactorStep() {
        val flow = SteamWebLoginFlowFactory.from(
            SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(
                    stage = SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                    failureReason = SteamWebLoginFailureReason.INVALID_TWO_FACTOR_CODE,
                ),
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.FAILURE,
                    stage = SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                    failureReason = SteamWebLoginFailureReason.INVALID_TWO_FACTOR_CODE,
                ),
            ),
        )

        assertEquals(SteamWebLoginNextActionKind.ENTER_TWO_FACTOR_CODE, flow.nextAction.kind)
        assertEquals(
            SteamWebLoginFailureReason.INVALID_TWO_FACTOR_CODE,
            flow.nextAction.failureReason,
        )
        assertTrue(flow.acceptsCodeInput)
        assertEquals(SteamWebLoginStage.TWO_FACTOR_REQUIRED, flow.assistStage)
    }

    @Test
    fun from_withAdditionalChallenge_requiresManualCompletion() {
        val flow = SteamWebLoginFlowFactory.from(
            SteamWebLoginTransaction(
                analysis = SteamWebLoginAnalysis(SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED),
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.ADVANCED,
                    stage = SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED,
                    submittedStage = SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                ),
            ),
        )

        assertEquals(
            SteamWebLoginNextActionKind.COMPLETE_ADDITIONAL_CHALLENGE,
            flow.nextAction.kind,
        )
        assertFalse(flow.acceptsCredentialsInput)
        assertFalse(flow.acceptsCodeInput)
    }
}
