package com.example.steam_vault_app.feature.steamsession

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamWebLoginJourneyFactoryTest {
    @Test
    fun from_withTwoFactorAfterCredentials_marksCredentialCompletedAndEmailSkipped() {
        val journey = SteamWebLoginJourneyFactory.from(
            SteamWebLoginFlowFactory.from(
                SteamWebLoginTransaction(
                    analysis = SteamWebLoginAnalysis(SteamWebLoginStage.TWO_FACTOR_REQUIRED),
                    completedStages = listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
                    lastProgress = SteamWebLoginProgress(
                        kind = SteamWebLoginProgressKind.ADVANCED,
                        stage = SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                        submittedStage = SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                SteamWebLoginJourneyStepState.COMPLETED,
                SteamWebLoginJourneyStepState.SKIPPED,
                SteamWebLoginJourneyStepState.ACTIVE,
                SteamWebLoginJourneyStepState.UPCOMING,
                SteamWebLoginJourneyStepState.UPCOMING,
            ),
            journey.steps.map { it.state },
        )
    }

    @Test
    fun from_withFailedEmailCode_marksRetryOnlyOnEmailStep() {
        val journey = SteamWebLoginJourneyFactory.from(
            SteamWebLoginFlowFactory.from(
                SteamWebLoginTransaction(
                    analysis = SteamWebLoginAnalysis(
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                    completedStages = listOf(SteamWebLoginStage.WAITING_FOR_CREDENTIALS),
                    lastProgress = SteamWebLoginProgress(
                        kind = SteamWebLoginProgressKind.FAILURE,
                        stage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        submittedStage = SteamWebLoginStage.EMAIL_CODE_REQUIRED,
                        failureReason = SteamWebLoginFailureReason.INVALID_EMAIL_CODE,
                    ),
                ),
            ),
        )

        assertEquals(SteamWebLoginJourneyStepState.COMPLETED, journey.steps[0].state)
        assertEquals(SteamWebLoginJourneyStepState.RETRY_REQUIRED, journey.steps[1].state)
        assertEquals(SteamWebLoginJourneyStepState.UPCOMING, journey.steps[2].state)
    }

    @Test
    fun from_withSessionReady_marksReadyAndPreviousStepsCompletedOrSkipped() {
        val journey = SteamWebLoginJourneyFactory.from(
            SteamWebLoginFlowFactory.from(
                SteamWebLoginTransaction(
                    analysis = SteamWebLoginAnalysis(SteamWebLoginStage.SESSION_READY),
                    completedStages = listOf(
                        SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
                        SteamWebLoginStage.TWO_FACTOR_REQUIRED,
                        SteamWebLoginStage.SESSION_READY,
                    ),
                    lastProgress = SteamWebLoginProgress(
                        kind = SteamWebLoginProgressKind.SESSION_READY,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                ),
            ),
        )

        assertEquals(SteamWebLoginJourneyStepState.COMPLETED, journey.steps[0].state)
        assertEquals(SteamWebLoginJourneyStepState.SKIPPED, journey.steps[1].state)
        assertEquals(SteamWebLoginJourneyStepState.COMPLETED, journey.steps[2].state)
        assertEquals(SteamWebLoginJourneyStepState.SKIPPED, journey.steps[3].state)
        assertEquals(SteamWebLoginJourneyStepState.READY, journey.steps[4].state)
    }
}
