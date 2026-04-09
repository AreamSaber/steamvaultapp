package com.example.steam_vault_app.data.cloudbackup

import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupReason
import com.example.steam_vault_app.domain.model.CloudBackupAutoBackupState
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.domain.sync.CloudBackupSyncManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AutoCloudBackupScheduler {
    fun schedule(reason: CloudBackupAutoBackupReason)

    suspend fun cancelPendingUploadsForManualRestore()
}

class InProcessAutoCloudBackupScheduler(
    private val cloudBackupRepository: CloudBackupRepository,
    private val cloudBackupSyncManager: CloudBackupSyncManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val debounceMillis: Long = 10_000L,
    private val minUploadIntervalMillis: Long = 60_000L,
    private val retryDelaysMillis: List<Long> = listOf(30_000L, 120_000L, 300_000L),
) : AutoCloudBackupScheduler {
    private val mutex = Mutex()
    private var scheduledJob: Job? = null
    private var isRunning = false
    private var pendingReason: CloudBackupAutoBackupReason? = null
    @Volatile
    private var generation: Long = 0L
    private var runningUploadCompletion: CompletableDeferred<Unit>? = null

    override fun schedule(reason: CloudBackupAutoBackupReason) {
        val scheduleGeneration = generation
        scope.launch {
            runCatching {
                enqueueAutomaticUpload(
                    reason = reason,
                    state = CloudBackupAutoBackupState.SCHEDULED,
                    forcedDelayMillis = null,
                    expectedGeneration = scheduleGeneration,
                )
            }
        }
    }

    override suspend fun cancelPendingUploadsForManualRestore() {
        val runningCompletion = mutex.withLock {
            generation += 1
            pendingReason = null
            scheduledJob?.cancel()
            scheduledJob = null
            runningUploadCompletion
        }

        runningCompletion?.await()
        markPausedAfterManualRestore()
    }

    private suspend fun enqueueAutomaticUpload(
        reason: CloudBackupAutoBackupReason,
        state: CloudBackupAutoBackupState,
        forcedDelayMillis: Long?,
        expectedGeneration: Long?,
    ) {
        val currentGeneration = mutex.withLock { generation }
        if (expectedGeneration != null && currentGeneration != expectedGeneration) {
            return
        }

        val configuration = runCatching { cloudBackupRepository.getConfiguration() }.getOrNull() ?: return
        val currentStatus = runCatching { cloudBackupRepository.getStatus() }.getOrNull() ?: return
        val delayMillis = forcedDelayMillis ?: computeDelayMillis(currentStatus.lastUploadAt)
        val scheduledAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis())
        val nextRunAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis() + delayMillis)

        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                accountLabel = configuration.username,
                remotePath = configuration.remotePath,
                autoBackupState = state,
                autoBackupReason = reason,
                autoBackupUpdatedAt = scheduledAt,
                autoBackupNextRunAt = nextRunAt,
            ),
        )

        val launchGeneration = mutex.withLock {
            if (expectedGeneration != null && generation != expectedGeneration) {
                null
            } else {
                pendingReason = reason
                if (!isRunning) {
                    scheduledJob?.cancel()
                }
                generation
            }
        }
        if (launchGeneration == null) {
            return
        }

        val shouldCreateJob = mutex.withLock { !isRunning }
        if (!shouldCreateJob) {
            return
        }

        val job = scope.launch {
            delay(delayMillis)
            runCatching {
                runPendingUpload(launchGeneration)
            }
        }

        mutex.withLock {
            if (generation == launchGeneration) {
                scheduledJob = job
            } else {
                job.cancel()
            }
        }
    }

    private suspend fun runPendingUpload(expectedGeneration: Long) {
        val reason = mutex.withLock {
            if (isRunning || generation != expectedGeneration) {
                return
            }

            val nextReason = pendingReason ?: return
            pendingReason = null
            scheduledJob = null
            isRunning = true
            runningUploadCompletion = CompletableDeferred()
            nextReason
        }

        var retryDelayMillis: Long? = null
        val completionGeneration = mutex.withLock { generation }

        try {
            cloudBackupSyncManager.uploadCurrentBackup()
            if (isCurrentGeneration(completionGeneration)) {
                markSuccess(reason)
            }
        } catch (_: Exception) {
            if (isCurrentGeneration(completionGeneration)) {
                retryDelayMillis = markFailure(reason)
            }
        } finally {
            val nextReason = mutex.withLock {
                isRunning = false
                runningUploadCompletion?.complete(Unit)
                runningUploadCompletion = null
                if (generation != completionGeneration) {
                    null
                } else if (scheduledJob != null) {
                    null
                } else {
                    pendingReason.also { pendingReason = null }
                }
            }

            if (nextReason != null) {
                enqueueAutomaticUpload(
                    reason = nextReason,
                    state = if (retryDelayMillis != null) {
                        CloudBackupAutoBackupState.RETRY_SCHEDULED
                    } else {
                        CloudBackupAutoBackupState.SCHEDULED
                    },
                    forcedDelayMillis = retryDelayMillis,
                    expectedGeneration = completionGeneration,
                )
            }
        }
    }

    private suspend fun markSuccess(reason: CloudBackupAutoBackupReason) {
        val currentStatus = cloudBackupRepository.getStatus()
        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                autoBackupState = CloudBackupAutoBackupState.SUCCESS,
                autoBackupReason = reason,
                autoBackupUpdatedAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis()),
                autoBackupNextRunAt = null,
                autoBackupFailureCount = 0,
            ),
        )
    }

    private suspend fun markFailure(reason: CloudBackupAutoBackupReason): Long? {
        val currentStatus = cloudBackupRepository.getStatus()
        val nextFailureCount = currentStatus.autoBackupFailureCount + 1
        val retryDelayMillis = retryDelaysMillis.getOrNull(nextFailureCount - 1)
        val nextState = if (retryDelayMillis == null) {
            CloudBackupAutoBackupState.ERROR
        } else {
            CloudBackupAutoBackupState.RETRY_SCHEDULED
        }

        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                autoBackupState = nextState,
                autoBackupReason = reason,
                autoBackupUpdatedAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis()),
                autoBackupNextRunAt = retryDelayMillis?.let {
                    CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis() + it)
                },
                autoBackupFailureCount = nextFailureCount,
            ),
        )

        mutex.withLock {
            if (pendingReason == null && retryDelayMillis != null) {
                pendingReason = reason
            }
        }

        return retryDelayMillis
    }

    private suspend fun markPausedAfterManualRestore() {
        val currentStatus = runCatching { cloudBackupRepository.getStatus() }.getOrNull() ?: return
        val configuration = runCatching { cloudBackupRepository.getConfiguration() }.getOrNull()
        cloudBackupRepository.saveStatus(
            currentStatus.copy(
                accountLabel = configuration?.username ?: currentStatus.accountLabel,
                remotePath = configuration?.remotePath ?: currentStatus.remotePath,
                autoBackupState = CloudBackupAutoBackupState.PAUSED_AFTER_RESTORE,
                autoBackupReason = CloudBackupAutoBackupReason.MANUAL_RESTORE,
                autoBackupUpdatedAt = CloudBackupAutoBackupTiming.formatIsoUtc(nowMillis()),
                autoBackupNextRunAt = null,
                autoBackupFailureCount = 0,
            ),
        )
    }

    private suspend fun isCurrentGeneration(expectedGeneration: Long): Boolean {
        return mutex.withLock { generation == expectedGeneration }
    }

    private fun computeDelayMillis(lastUploadAt: String?): Long {
        return CloudBackupAutoBackupTiming.computeDelayMillis(
            lastUploadAt = lastUploadAt,
            nowMillis = nowMillis(),
            debounceMillis = debounceMillis,
            minUploadIntervalMillis = minUploadIntervalMillis,
        )
    }
}
