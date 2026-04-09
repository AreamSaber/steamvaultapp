package com.example.steam_vault_app.feature.steamsession

import java.io.Serializable

internal data class SteamWebLoginTransaction(
    val currentUrl: String = SteamWebLoginSessionCapture.loginUrl,
    val pageTitle: String = "",
    val analysis: SteamWebLoginAnalysis = SteamWebLoginAnalysis(SteamWebLoginStage.UNKNOWN),
    val pendingAssistStage: SteamWebLoginStage? = null,
    val lastHandledCaptureSignature: String? = null,
    val attemptNumber: Int = 0,
    val completedStages: List<SteamWebLoginStage> = emptyList(),
    val state: SteamWebLoginTransactionState = SteamWebLoginTransactionState.IDLE,
    val result: SteamWebLoginTransactionResult? = null,
    val events: List<SteamWebLoginTransactionEvent> = emptyList(),
    val history: List<SteamWebLoginAttemptRecord> = emptyList(),
    val lastProgress: SteamWebLoginProgress = SteamWebLoginProgress(
        kind = SteamWebLoginProgressKind.WAITING,
        stage = SteamWebLoginStage.UNKNOWN,
    ),
) : Serializable

internal enum class SteamWebLoginTransactionState {
    IDLE,
    ACTIVE,
    WAITING_RESULT,
    SESSION_CAPTURED,
    SAVED,
    FAILED,
}

internal enum class SteamWebLoginTransactionResult {
    FAILURE_DETECTED,
    SESSION_CAPTURED,
    SESSION_SAVED,
    SESSION_SAVE_FAILED,
    DUPLICATE_SESSION,
}

internal data class SteamWebLoginAttemptRecord(
    val attemptNumber: Int,
    val result: SteamWebLoginTransactionResult,
    val finalStage: SteamWebLoginStage,
    val failureReason: SteamWebLoginFailureReason? = null,
    val completedStages: List<SteamWebLoginStage> = emptyList(),
    val eventCount: Int = 0,
    val timeline: List<SteamWebLoginTransactionEvent> = emptyList(),
    val summary: SteamWebLoginAttemptTimelineSummary = SteamWebLoginAttemptTimelineSummary(),
) : Serializable

internal enum class SteamWebLoginTransactionEventType {
    TRANSACTION_STARTED,
    CREDENTIALS_SUBMITTED,
    EMAIL_CODE_SUBMITTED,
    TWO_FACTOR_SUBMITTED,
    ASSIST_REJECTED,
    STAGE_ADVANCED,
    FAILURE_DETECTED,
    SESSION_CAPTURED,
    SESSION_SAVED,
    SESSION_SAVE_FAILED,
}

internal data class SteamWebLoginTransactionEvent(
    val type: SteamWebLoginTransactionEventType,
    val stage: SteamWebLoginStage,
    val failureReason: SteamWebLoginFailureReason? = null,
) : Serializable

internal enum class SteamWebLoginProgressKind {
    WAITING,
    PENDING_RESULT,
    ADVANCED,
    FAILURE,
    SESSION_READY,
    DUPLICATE_SESSION,
}

internal data class SteamWebLoginProgress(
    val kind: SteamWebLoginProgressKind,
    val stage: SteamWebLoginStage,
    val submittedStage: SteamWebLoginStage? = null,
    val failureReason: SteamWebLoginFailureReason? = null,
) : Serializable

internal data class SteamWebLoginTransactionUpdate(
    val transaction: SteamWebLoginTransaction,
    val progress: SteamWebLoginProgress,
    val capture: SteamWebLoginCapture? = null,
)

internal object SteamWebLoginTransactionReducer {
    fun markAssistSubmitted(
        transaction: SteamWebLoginTransaction,
        stage: SteamWebLoginStage,
    ): SteamWebLoginTransaction {
        val preparedTransaction = ensureStarted(
            prepareForNewAttempt(transaction, shouldStartFresh = true),
            stage,
        )
        return preparedTransaction.copy(
            pendingAssistStage = stage,
            state = SteamWebLoginTransactionState.WAITING_RESULT,
            result = null,
            events = appendEvent(
                events = preparedTransaction.events,
                event = SteamWebLoginTransactionEvent(
                    type = submissionEventType(stage),
                    stage = stage,
                ),
            ),
            lastProgress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.PENDING_RESULT,
                stage = stage,
                submittedStage = stage,
            ),
        )
    }

    fun markAssistRejected(transaction: SteamWebLoginTransaction): SteamWebLoginTransaction {
        return transaction.copy(
            pendingAssistStage = null,
            state = SteamWebLoginTransactionState.ACTIVE,
            result = null,
            events = appendEvent(
                events = transaction.events,
                event = SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.ASSIST_REJECTED,
                    stage = transaction.analysis.stage,
                ),
            ),
            lastProgress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = transaction.analysis.stage,
            ),
        )
    }

    fun markCapturePersistFailed(transaction: SteamWebLoginTransaction): SteamWebLoginTransaction {
        return transaction.copy(
            lastHandledCaptureSignature = null,
            state = SteamWebLoginTransactionState.FAILED,
            result = SteamWebLoginTransactionResult.SESSION_SAVE_FAILED,
            events = appendEvent(
                events = transaction.events,
                event = SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.SESSION_SAVE_FAILED,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
            ),
        )
    }

    fun markCapturePersisted(transaction: SteamWebLoginTransaction): SteamWebLoginTransaction {
        return transaction.copy(
            state = SteamWebLoginTransactionState.SAVED,
            result = SteamWebLoginTransactionResult.SESSION_SAVED,
            events = appendEvent(
                events = transaction.events,
                event = SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.SESSION_SAVED,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
            ),
        )
    }

    fun resetForHidden(transaction: SteamWebLoginTransaction): SteamWebLoginTransaction {
        val preparedTransaction = archiveClosedAttempt(transaction)
        return preparedTransaction.copy(
            pageTitle = "",
            analysis = SteamWebLoginAnalysis(SteamWebLoginStage.UNKNOWN),
            pendingAssistStage = null,
            completedStages = emptyList(),
            state = SteamWebLoginTransactionState.IDLE,
            result = null,
            events = emptyList(),
            lastProgress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = SteamWebLoginStage.UNKNOWN,
            ),
        )
    }

    fun resetForReload(transaction: SteamWebLoginTransaction): SteamWebLoginTransaction {
        val preparedTransaction = archiveClosedAttempt(transaction)
        return preparedTransaction.copy(
            currentUrl = SteamWebLoginSessionCapture.loginUrl,
            pageTitle = "",
            analysis = SteamWebLoginAnalysis(SteamWebLoginStage.UNKNOWN),
            pendingAssistStage = null,
            completedStages = emptyList(),
            state = SteamWebLoginTransactionState.IDLE,
            result = null,
            events = emptyList(),
            lastProgress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = SteamWebLoginStage.UNKNOWN,
            ),
        )
    }

    fun observePage(
        transaction: SteamWebLoginTransaction,
        observation: SteamWebLoginObservation,
    ): SteamWebLoginTransactionUpdate {
        val analysis = SteamWebLoginSessionCapture.analyzeObservation(observation)
        val preparedTransaction = prepareForNewAttempt(
            transaction = transaction,
            shouldStartFresh = analysis.stage != SteamWebLoginStage.UNKNOWN,
        )
        val baseTransaction = ensureStarted(
            transaction = preparedTransaction.copy(
                currentUrl = observation.currentUrl,
                pageTitle = observation.pageTitle.orEmpty(),
                analysis = analysis,
            ),
            stage = analysis.stage,
        )
        val capture = SteamWebLoginSessionCapture.captureFromCookieHeader(
            cookieHeader = observation.cookieHeader,
            currentUrl = observation.currentUrl,
        )

        if (capture == null) {
            val progress = resolveProgress(
                stage = analysis.stage,
                pendingAssistStage = baseTransaction.pendingAssistStage,
                failureReason = analysis.failureReason,
            )
            val transactionState = stateForProgress(progress)
            val progressEvent = eventForProgressChange(
                previous = transaction.lastProgress,
                current = progress,
            )
            return SteamWebLoginTransactionUpdate(
                transaction = baseTransaction.copy(
                    pendingAssistStage = if (progress.kind == SteamWebLoginProgressKind.PENDING_RESULT) {
                        baseTransaction.pendingAssistStage
                    } else {
                        null
                    },
                    completedStages = updateCompletedStages(
                        completedStages = baseTransaction.completedStages,
                        progress = progress,
                    ),
                    state = transactionState,
                    result = resultForProgress(progress),
                    events = progressEvent?.let { event ->
                        appendEvent(baseTransaction.events, event)
                    } ?: baseTransaction.events,
                    lastProgress = progress,
                ),
                progress = progress,
            )
        }

        if (capture.signature == transaction.lastHandledCaptureSignature) {
            return SteamWebLoginTransactionUpdate(
                transaction = baseTransaction.copy(
                    pendingAssistStage = null,
                    completedStages = updateCompletedStages(
                        completedStages = baseTransaction.completedStages,
                        progress = SteamWebLoginProgress(
                            kind = SteamWebLoginProgressKind.DUPLICATE_SESSION,
                            stage = SteamWebLoginStage.SESSION_READY,
                        ),
                    ),
                    state = SteamWebLoginTransactionState.SESSION_CAPTURED,
                    result = SteamWebLoginTransactionResult.DUPLICATE_SESSION,
                    events = appendEvent(
                        events = baseTransaction.events,
                        event = SteamWebLoginTransactionEvent(
                            type = SteamWebLoginTransactionEventType.SESSION_CAPTURED,
                            stage = SteamWebLoginStage.SESSION_READY,
                        ),
                    ),
                    lastProgress = SteamWebLoginProgress(
                        kind = SteamWebLoginProgressKind.DUPLICATE_SESSION,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                ),
                progress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.DUPLICATE_SESSION,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
                capture = capture,
            )
        }

        return SteamWebLoginTransactionUpdate(
            transaction = baseTransaction.copy(
                pendingAssistStage = null,
                lastHandledCaptureSignature = capture.signature,
                completedStages = updateCompletedStages(
                    completedStages = baseTransaction.completedStages,
                    progress = SteamWebLoginProgress(
                        kind = SteamWebLoginProgressKind.SESSION_READY,
                        stage = SteamWebLoginStage.SESSION_READY,
                        submittedStage = baseTransaction.pendingAssistStage,
                    ),
                ),
                state = SteamWebLoginTransactionState.SESSION_CAPTURED,
                result = SteamWebLoginTransactionResult.SESSION_CAPTURED,
                events = appendEvent(
                    events = baseTransaction.events,
                    event = SteamWebLoginTransactionEvent(
                        type = SteamWebLoginTransactionEventType.SESSION_CAPTURED,
                        stage = SteamWebLoginStage.SESSION_READY,
                    ),
                ),
                lastProgress = SteamWebLoginProgress(
                    kind = SteamWebLoginProgressKind.SESSION_READY,
                    stage = SteamWebLoginStage.SESSION_READY,
                ),
            ),
            progress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.SESSION_READY,
                stage = SteamWebLoginStage.SESSION_READY,
            ),
            capture = capture,
        )
    }

    private fun resolveProgress(
        stage: SteamWebLoginStage,
        pendingAssistStage: SteamWebLoginStage?,
        failureReason: SteamWebLoginFailureReason?,
    ): SteamWebLoginProgress {
        if (failureReason != null) {
            return SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.FAILURE,
                stage = stage,
                submittedStage = pendingAssistStage,
                failureReason = failureReason,
            )
        }

        if (pendingAssistStage == null) {
            return SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = stage,
            )
        }

        return if (stage == pendingAssistStage) {
            SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.PENDING_RESULT,
                stage = stage,
                submittedStage = pendingAssistStage,
            )
        } else if (hasAdvancedFromSubmittedStage(pendingAssistStage, stage)) {
            SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.ADVANCED,
                stage = stage,
                submittedStage = pendingAssistStage,
            )
        } else {
            SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = stage,
            )
        }
    }

    private fun hasAdvancedFromSubmittedStage(
        submittedStage: SteamWebLoginStage,
        currentStage: SteamWebLoginStage,
    ): Boolean {
        return when (submittedStage) {
            SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
                currentStage == SteamWebLoginStage.EMAIL_CODE_REQUIRED ||
                    currentStage == SteamWebLoginStage.TWO_FACTOR_REQUIRED ||
                    currentStage == SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED
            }

            SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                currentStage == SteamWebLoginStage.TWO_FACTOR_REQUIRED ||
                    currentStage == SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED
            }

            SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                currentStage == SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED
            }

            SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED,
            SteamWebLoginStage.SESSION_READY,
            SteamWebLoginStage.UNKNOWN,
            -> false
        }
    }

    private fun updateCompletedStages(
        completedStages: List<SteamWebLoginStage>,
        progress: SteamWebLoginProgress,
    ): List<SteamWebLoginStage> {
        val mutableStages = completedStages.toMutableList()
        when (progress.kind) {
            SteamWebLoginProgressKind.ADVANCED -> {
                progress.submittedStage?.let { stage ->
                    appendCompletedStage(mutableStages, stage)
                }
            }

            SteamWebLoginProgressKind.SESSION_READY,
            SteamWebLoginProgressKind.DUPLICATE_SESSION,
            -> {
                progress.submittedStage?.let { stage ->
                    appendCompletedStage(mutableStages, stage)
                }
                appendCompletedStage(mutableStages, SteamWebLoginStage.SESSION_READY)
            }

            SteamWebLoginProgressKind.WAITING,
            SteamWebLoginProgressKind.PENDING_RESULT,
            SteamWebLoginProgressKind.FAILURE,
            -> Unit
        }
        return mutableStages
    }

    private fun appendCompletedStage(
        stages: MutableList<SteamWebLoginStage>,
        stage: SteamWebLoginStage,
    ) {
        if (stage == SteamWebLoginStage.UNKNOWN || stages.contains(stage)) {
            return
        }
        stages += stage
    }

    private fun archiveClosedAttempt(
        transaction: SteamWebLoginTransaction,
    ): SteamWebLoginTransaction {
        if (transaction.result == null || transaction.events.isEmpty()) {
            return transaction
        }
        return transaction.copy(
            history = transaction.history + archiveAttempt(transaction),
        )
    }

    private fun prepareForNewAttempt(
        transaction: SteamWebLoginTransaction,
        shouldStartFresh: Boolean,
    ): SteamWebLoginTransaction {
        if (!shouldStartFresh || transaction.result == null || transaction.events.isEmpty()) {
            return transaction
        }
        val archivedTransaction = archiveClosedAttempt(transaction)
        return archivedTransaction.copy(
            pendingAssistStage = null,
            completedStages = emptyList(),
            state = SteamWebLoginTransactionState.IDLE,
            result = null,
            events = emptyList(),
            lastProgress = SteamWebLoginProgress(
                kind = SteamWebLoginProgressKind.WAITING,
                stage = SteamWebLoginStage.UNKNOWN,
            ),
        )
    }

    private fun archiveAttempt(
        transaction: SteamWebLoginTransaction,
    ): SteamWebLoginAttemptRecord {
        val attempt = SteamWebLoginAttemptRecord(
            attemptNumber = transaction.attemptNumber,
            result = transaction.result ?: SteamWebLoginTransactionResult.FAILURE_DETECTED,
            finalStage = transaction.analysis.stage,
            failureReason = transaction.lastProgress.failureReason,
            completedStages = transaction.completedStages,
            eventCount = transaction.events.size,
            timeline = transaction.events,
        )
        return attempt.copy(
            summary = SteamWebLoginAttemptTimelineSummaryFactory.from(attempt),
        )
    }

    private fun ensureStarted(
        transaction: SteamWebLoginTransaction,
        stage: SteamWebLoginStage,
    ): SteamWebLoginTransaction {
        if (stage == SteamWebLoginStage.UNKNOWN || transaction.events.isNotEmpty()) {
            return transaction
        }
        return transaction.copy(
            attemptNumber = transaction.attemptNumber + 1,
            state = SteamWebLoginTransactionState.ACTIVE,
            result = null,
            events = appendEvent(
                events = transaction.events,
                event = SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.TRANSACTION_STARTED,
                    stage = stage,
                ),
            ),
        )
    }

    private fun submissionEventType(stage: SteamWebLoginStage): SteamWebLoginTransactionEventType {
        return when (stage) {
            SteamWebLoginStage.WAITING_FOR_CREDENTIALS -> {
                SteamWebLoginTransactionEventType.CREDENTIALS_SUBMITTED
            }

            SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                SteamWebLoginTransactionEventType.EMAIL_CODE_SUBMITTED
            }

            SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                SteamWebLoginTransactionEventType.TWO_FACTOR_SUBMITTED
            }

            SteamWebLoginStage.ADDITIONAL_CHALLENGE_REQUIRED,
            SteamWebLoginStage.SESSION_READY,
            SteamWebLoginStage.UNKNOWN,
            -> {
                SteamWebLoginTransactionEventType.ASSIST_REJECTED
            }
        }
    }

    private fun stateForProgress(
        progress: SteamWebLoginProgress,
    ): SteamWebLoginTransactionState {
        return when (progress.kind) {
            SteamWebLoginProgressKind.WAITING,
            SteamWebLoginProgressKind.ADVANCED,
            -> {
                SteamWebLoginTransactionState.ACTIVE
            }

            SteamWebLoginProgressKind.PENDING_RESULT -> {
                SteamWebLoginTransactionState.WAITING_RESULT
            }

            SteamWebLoginProgressKind.FAILURE -> {
                SteamWebLoginTransactionState.FAILED
            }

            SteamWebLoginProgressKind.SESSION_READY,
            SteamWebLoginProgressKind.DUPLICATE_SESSION,
            -> {
                SteamWebLoginTransactionState.SESSION_CAPTURED
            }
        }
    }

    private fun resultForProgress(
        progress: SteamWebLoginProgress,
    ): SteamWebLoginTransactionResult? {
        return when (progress.kind) {
            SteamWebLoginProgressKind.FAILURE -> {
                SteamWebLoginTransactionResult.FAILURE_DETECTED
            }

            SteamWebLoginProgressKind.WAITING,
            SteamWebLoginProgressKind.PENDING_RESULT,
            SteamWebLoginProgressKind.ADVANCED,
            SteamWebLoginProgressKind.SESSION_READY,
            SteamWebLoginProgressKind.DUPLICATE_SESSION,
            -> null
        }
    }

    private fun eventForProgressChange(
        previous: SteamWebLoginProgress,
        current: SteamWebLoginProgress,
    ): SteamWebLoginTransactionEvent? {
        if (previous == current) {
            return null
        }
        return when (current.kind) {
            SteamWebLoginProgressKind.ADVANCED -> {
                SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.STAGE_ADVANCED,
                    stage = current.stage,
                )
            }

            SteamWebLoginProgressKind.FAILURE -> {
                SteamWebLoginTransactionEvent(
                    type = SteamWebLoginTransactionEventType.FAILURE_DETECTED,
                    stage = current.stage,
                    failureReason = current.failureReason,
                )
            }

            SteamWebLoginProgressKind.WAITING,
            SteamWebLoginProgressKind.PENDING_RESULT,
            SteamWebLoginProgressKind.SESSION_READY,
            SteamWebLoginProgressKind.DUPLICATE_SESSION,
            -> null
        }
    }

    private fun appendEvent(
        events: List<SteamWebLoginTransactionEvent>,
        event: SteamWebLoginTransactionEvent,
    ): List<SteamWebLoginTransactionEvent> {
        if (events.lastOrNull() == event) {
            return events
        }
        return events + event
    }
}
