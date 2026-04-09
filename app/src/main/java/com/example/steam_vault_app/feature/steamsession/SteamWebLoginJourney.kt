package com.example.steam_vault_app.feature.steamsession

internal enum class SteamWebLoginJourneyStepState {
    UPCOMING,
    ACTIVE,
    PENDING_RESULT,
    RETRY_REQUIRED,
    COMPLETED,
    SKIPPED,
    MANUAL_REQUIRED,
    READY,
}

internal data class SteamWebLoginJourneyStep(
    val stage: SteamWebLoginStage,
    val state: SteamWebLoginJourneyStepState,
)

internal data class SteamWebLoginJourney(
    val flow: SteamWebLoginFlow,
    val steps: List<SteamWebLoginJourneyStep>,
)

internal object SteamWebLoginJourneyFactory {
    private val orderedStages = listOf(
        SteamWebLoginStage.WAITING_FOR_CREDENTIALS,
        SteamWebLoginStage.EMAIL_CODE_REQUIRED,
        SteamWebLoginStage.TWO_FACTOR_REQUIRED,
        SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED,
        SteamWebLoginStage.SESSION_READY,
    )

    fun from(flow: SteamWebLoginFlow): SteamWebLoginJourney {
        val transaction = flow.transaction
        val currentStage = flow.stage
        val currentIndex = orderedStages.indexOf(currentStage)
        val completedStages = transaction.completedStages.toSet()
        val steps = orderedStages.mapIndexed { index, stage ->
            SteamWebLoginJourneyStep(
                stage = stage,
                state = when {
                    stage == SteamWebLoginStage.SESSION_READY &&
                        flow.nextAction.kind == SteamWebLoginNextActionKind.SESSION_READY -> {
                        SteamWebLoginJourneyStepState.READY
                    }

                    stage == currentStage &&
                        flow.progress.kind == SteamWebLoginProgressKind.PENDING_RESULT -> {
                        SteamWebLoginJourneyStepState.PENDING_RESULT
                    }

                    stage == currentStage &&
                        flow.progress.kind == SteamWebLoginProgressKind.FAILURE -> {
                        SteamWebLoginJourneyStepState.RETRY_REQUIRED
                    }

                    stage == currentStage &&
                        flow.nextAction.kind == SteamWebLoginNextActionKind.COMPLETE_ADDITIONAL_CHALLENGE -> {
                        SteamWebLoginJourneyStepState.MANUAL_REQUIRED
                    }

                    stage == currentStage -> {
                        SteamWebLoginJourneyStepState.ACTIVE
                    }

                    stage in completedStages -> {
                        SteamWebLoginJourneyStepState.COMPLETED
                    }

                    currentIndex >= 0 && index < currentIndex -> {
                        SteamWebLoginJourneyStepState.SKIPPED
                    }

                    else -> {
                        SteamWebLoginJourneyStepState.UPCOMING
                    }
                },
            )
        }

        return SteamWebLoginJourney(
            flow = flow,
            steps = steps,
        )
    }
}
