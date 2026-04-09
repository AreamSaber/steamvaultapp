package com.example.steam_vault_app.feature.steamsession

import com.example.steam_vault_app.data.importing.SteamImportParser
import com.example.steam_vault_app.data.security.SteamSecretCodec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.model.SteamGuardAccountSnapshot
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginMode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.domain.sync.SteamSessionValidationSyncManager
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultKeyValueRow
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultProgressSteps
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultStepItem
import com.example.steam_vault_app.ui.common.VaultStepState
import com.example.steam_vault_app.ui.common.VaultTextField
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SteamSessionScreen(
    tokenId: String,
    vaultRepository: VaultRepository,
    steamSessionRepository: SteamSessionRepository,
    steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    steamSessionValidationSyncManager: SteamSessionValidationSyncManager,
    vaultCryptography: VaultCryptography,
    importParser: SteamImportParser,
    steamTimeSyncState: SteamTimeSyncState,
    onSyncSteamTime: () -> Unit,
    onOpenConfirmations: () -> Unit,
    refreshVersion: Int,
    entryContext: SteamSessionEntryContext? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var clearMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var clearError by rememberSaveable { mutableStateOf<String?>(null) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var isValidatingSession by rememberSaveable { mutableStateOf(false) }
    var localRefreshVersion by rememberSaveable { mutableStateOf(0) }
    var repairPayloadInput by rememberSaveable { mutableStateOf("") }
    var repairMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var repairError by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryUsernameInput by rememberSaveable { mutableStateOf("") }
    var recoveryPasswordInput by rememberSaveable { mutableStateOf("") }
    var recoveryStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isRecoveringSession by rememberSaveable { mutableStateOf(false) }
    var recoveryChallengeCodeInput by rememberSaveable { mutableStateOf("") }
    var pendingRecoveryChallenge by remember { mutableStateOf<SteamProtocolLoginChallenge?>(null) }
    var pendingRecoveryResponse by remember {
        mutableStateOf<CompletableDeferred<SteamProtocolLoginChallengeAnswer>?>(null)
    }
    var recoveryJob by remember { mutableStateOf<Job?>(null) }
    var steamIdInput by rememberSaveable { mutableStateOf("") }
    var sessionIdInput by rememberSaveable { mutableStateOf("") }
    var oauthTokenInput by rememberSaveable { mutableStateOf("") }
    var rawCookiesInput by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            recoveryJob?.cancel()
            pendingRecoveryResponse?.complete(SteamProtocolLoginChallengeAnswer.Cancelled)
            pendingRecoveryResponse = null
        }
    }

    val sessionState by produceState<AppUiState<SteamSessionScreenState>>(
        initialValue = AppUiState.Loading,
        key1 = tokenId,
        key2 = refreshVersion,
        key3 = localRefreshVersion,
    ) {
        value = try {
            val token = vaultRepository.getToken(tokenId)
            if (token == null) {
                AppUiState.Error(context.getString(R.string.steam_session_error_not_found))
            } else {
                AppUiState.Success(
                    SteamSessionScreenState(
                        token = token,
                        session = steamSessionRepository.getSession(tokenId),
                    ),
                )
            }
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.steam_session_error_load_failed))
        }
    }

    val currentSessionState = (sessionState as? AppUiState.Success)?.value
    LaunchedEffect(
        currentSessionState?.token?.id,
        currentSessionState?.session?.updatedAt,
    ) {
        val session = currentSessionState?.session
        steamIdInput = session?.steamId.orEmpty()
        sessionIdInput = session?.sessionId.orEmpty()
        oauthTokenInput = session?.oauthToken.orEmpty()
        rawCookiesInput = SteamSessionEditorParser.formatCookies(session?.cookies.orEmpty())
    }
    LaunchedEffect(
        currentSessionState?.token?.id,
        currentSessionState?.session?.updatedAt,
        entryContext?.kind,
        entryContext?.suggestedSteamId,
    ) {
        val suggestedSteamId = entryContext?.suggestedSteamId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (currentSessionState?.session == null && steamIdInput.isBlank() && suggestedSteamId != null) {
            steamIdInput = suggestedSteamId
        }
    }
    LaunchedEffect(currentSessionState?.token?.id) {
        recoveryUsernameInput = currentSessionState?.token?.accountName.orEmpty()
        recoveryPasswordInput = ""
    }

    fun submitRecoveryChallenge(answer: SteamProtocolLoginChallengeAnswer) {
        val deferred = pendingRecoveryResponse ?: return
        pendingRecoveryResponse = null
        pendingRecoveryChallenge = null
        recoveryChallengeCodeInput = ""
        deferred.complete(answer)
    }

    suspend fun awaitRecoveryChallenge(
        challenge: SteamProtocolLoginChallenge,
    ): SteamProtocolLoginChallengeAnswer {
        recoveryChallengeCodeInput = ""
        pendingRecoveryChallenge = challenge
        val deferred = CompletableDeferred<SteamProtocolLoginChallengeAnswer>()
        pendingRecoveryResponse = deferred
        return try {
            deferred.await()
        } finally {
            if (pendingRecoveryResponse === deferred) {
                pendingRecoveryResponse = null
            }
            if (pendingRecoveryChallenge == challenge) {
                pendingRecoveryChallenge = null
            }
            recoveryChallengeCodeInput = ""
        }
    }

    fun cancelProtocolRecovery(cancelMessage: String? = null) {
        recoveryJob?.cancel()
        recoveryJob = null
        pendingRecoveryResponse?.complete(SteamProtocolLoginChallengeAnswer.Cancelled)
        pendingRecoveryResponse = null
        pendingRecoveryChallenge = null
        recoveryChallengeCodeInput = ""
        isRecoveringSession = false
        recoveryStatusMessage = cancelMessage
        recoveryErrorMessage = null
    }

    fun startProtocolRecovery(
        token: TokenRecord,
        session: SteamSessionRecord?,
    ) {
        if (recoveryPasswordInput.isBlank()) {
            recoveryErrorMessage = context.getString(
                R.string.steam_session_protocol_repair_password_required,
            )
            recoveryStatusMessage = null
            return
        }

        recoveryJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = scope.launch {
            isRecoveringSession = true
            pendingRecoveryChallenge = null
            pendingRecoveryResponse = null
            recoveryChallengeCodeInput = ""
            recoveryErrorMessage = null
            recoveryStatusMessage = context.getString(R.string.steam_session_protocol_repair_started)
            try {
                val loginResult = steamProtocolLoginOrchestrator.login(
                    request = SteamProtocolLoginRequest(
                        username = recoveryUsernameInput.trim().ifBlank { token.accountName },
                        password = recoveryPasswordInput,
                        mode = SteamProtocolLoginMode.IMPORT,
                        existingAccount = SteamGuardAccountSnapshot.fromLegacy(
                            token = token,
                            session = session,
                        ),
                    ),
                    respondToChallenge = { challenge ->
                        when (challenge) {
                            is SteamProtocolLoginChallenge.DeviceCode -> {
                                try {
                                    if (challenge.previousCodeWasIncorrect) {
                                        recoveryStatusMessage = context.getString(
                                            R.string.steam_session_protocol_repair_waiting_next_code,
                                        )
                                        delay(
                                            millisUntilNextSteamCodeWindow(
                                                steamTimeSyncState.offsetSeconds,
                                            ),
                                        )
                                    }
                                    val code = generateCurrentSteamGuardCode(
                                        token = token,
                                        vaultCryptography = vaultCryptography,
                                        offsetSeconds = steamTimeSyncState.offsetSeconds,
                                    )
                                    recoveryStatusMessage = context.getString(
                                        R.string.steam_session_protocol_repair_auto_code_submitted,
                                    )
                                    SteamProtocolLoginChallengeAnswer.Code(code)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    recoveryStatusMessage = context.getString(
                                        R.string.steam_session_protocol_repair_auto_code_failed,
                                    )
                                    awaitRecoveryChallenge(challenge)
                                }
                            }

                            is SteamProtocolLoginChallenge.EmailCode,
                            is SteamProtocolLoginChallenge.DeviceConfirmation,
                            -> awaitRecoveryChallenge(challenge)

                            is SteamProtocolLoginChallenge.QrCode -> {
                                throw IllegalStateException(
                                    context.getString(
                                        R.string.steam_session_protocol_repair_qr_not_supported,
                                    ),
                                )
                            }
                        }
                    },
                )
                steamProtocolLoginOrchestrator.saveSessionForToken(
                    tokenId = token.id,
                    accountName = token.accountName,
                    session = loginResult.session,
                    updatedAt = Instant.now().toString(),
                )
                recoveryPasswordInput = ""
                recoveryStatusMessage = context.getString(
                    R.string.steam_session_protocol_repair_success,
                    token.accountName,
                )
                recoveryErrorMessage = null
                localRefreshVersion += 1
            } catch (_: CancellationException) {
                cancelProtocolRecovery(
                    context.getString(R.string.steam_session_protocol_repair_cancelled),
                )
            } catch (error: Exception) {
                pendingRecoveryChallenge = null
                pendingRecoveryResponse = null
                recoveryErrorMessage = error.message
                    ?: context.getString(R.string.steam_session_protocol_repair_failed)
            } finally {
                isRecoveringSession = false
                if (recoveryJob === coroutineContext[Job] || recoveryJob === launchedJob) {
                    recoveryJob = null
                }
            }
        }
        recoveryJob = launchedJob
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (val currentState = sessionState) {
            AppUiState.Loading -> item {
                VaultInlineBanner(
                    text = stringResource(R.string.steam_session_loading),
                    tone = VaultBannerTone.Neutral,
                )
            }

            AppUiState.Empty -> Unit
            is AppUiState.Error -> item {
                VaultInlineBanner(
                    text = currentState.message,
                    tone = VaultBannerTone.Error,
                )
            }

            is AppUiState.Success -> {
                val token = currentState.value.token
                val session = currentState.value.session
                val prerequisitesReady = listOf(
                    !token.identitySecret.isNullOrBlank(),
                    !token.deviceId.isNullOrBlank(),
                    !token.tokenGid.isNullOrBlank(),
                    steamTimeSyncState.lastSyncAt != null,
                )
                val progressSteps = listOf(
                    VaultStepItem(
                        title = context.getString(R.string.steam_session_prerequisites_title),
                        subtitle = context.getString(R.string.steam_session_prerequisites_description),
                        state = if (prerequisitesReady.all { it }) {
                            VaultStepState.Complete
                        } else {
                            VaultStepState.Active
                        },
                    ),
                    VaultStepItem(
                        title = context.getString(R.string.steam_session_current_title),
                        subtitle = context.getString(R.string.steam_session_current_description),
                        state = if (session != null) {
                            VaultStepState.Complete
                        } else {
                            VaultStepState.Active
                        },
                    ),
                    VaultStepItem(
                        title = context.getString(R.string.steam_session_protocol_repair_title),
                        subtitle = context.getString(R.string.steam_session_protocol_repair_description),
                        state = when {
                            pendingRecoveryChallenge != null || isRecoveringSession -> VaultStepState.Active
                            session != null -> VaultStepState.Complete
                            else -> VaultStepState.Pending
                        },
                    ),
                )

                item {
                    VaultPageHeader(
                        eyebrow = stringResource(R.string.vault_brand_label),
                        title = stringResource(
                            R.string.steam_session_title_for_account,
                            token.accountName,
                        ),
                        subtitle = if (session == null) {
                            stringResource(R.string.steam_session_editor_description_new)
                        } else {
                            stringResource(R.string.steam_session_current_description)
                        },
                    )
                }
                item {
                    VaultProgressSteps(steps = progressSteps)
                }
                saveMessage?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Success,
                        )
                    }
                }
                saveError?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Error,
                        )
                    }
                }
                clearMessage?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Success,
                        )
                    }
                }
                clearError?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Error,
                        )
                    }
                }
                validationMessage?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Success,
                        )
                    }
                }
                validationError?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Error,
                        )
                    }
                }
                if (entryContext?.kind == SteamSessionEntryContext.Kind.IMPORTED_EXISTING_AUTHENTICATOR) {
                    item {
                        ScreenSectionCard(
                            title = stringResource(R.string.steam_session_import_repair_title),
                            description = stringResource(R.string.steam_session_import_repair_description),
                        ) {
                            entryContext.suggestedSteamId?.let { steamId ->
                                VaultKeyValueRow(
                                    label = stringResource(R.string.steam_session_detail_steam_id),
                                    value = steamId,
                                )
                            }
                            ChecklistRow(
                                label = stringResource(
                                    if (session == null) {
                                        R.string.steam_session_import_repair_session_missing
                                    } else {
                                        R.string.steam_session_import_repair_session_present
                                    },
                                ),
                                highlighted = session != null,
                            )
                            ChecklistRow(
                                label = stringResource(R.string.steam_session_import_repair_next_step_sda),
                                highlighted = true,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_session_prerequisites_title),
                        description = stringResource(R.string.steam_session_prerequisites_description),
                    ) {
                        ChecklistRow(
                            label = if (token.identitySecret.isNullOrBlank()) {
                                stringResource(R.string.steam_session_identity_missing)
                            } else {
                                stringResource(R.string.steam_session_identity_present)
                            },
                            highlighted = !token.identitySecret.isNullOrBlank(),
                        )
                        ChecklistRow(
                            label = if (token.deviceId.isNullOrBlank()) {
                                stringResource(R.string.steam_session_device_id_missing)
                            } else {
                                stringResource(R.string.steam_session_device_id_present)
                            },
                            highlighted = !token.deviceId.isNullOrBlank(),
                        )
                        ChecklistRow(
                            label = if (token.tokenGid.isNullOrBlank()) {
                                stringResource(R.string.steam_session_token_gid_missing)
                            } else {
                                stringResource(R.string.steam_session_token_gid_present)
                            },
                            highlighted = !token.tokenGid.isNullOrBlank(),
                        )
                        ChecklistRow(
                            label = if (steamTimeSyncState.lastSyncAt == null) {
                                stringResource(R.string.steam_session_time_not_synced)
                            } else {
                                stringResource(
                                    R.string.steam_session_time_synced,
                                    formatOffset(context, steamTimeSyncState.offsetSeconds),
                                )
                            },
                            highlighted = steamTimeSyncState.lastSyncAt != null,
                        )
                        VaultPrimaryButton(
                            text = stringResource(R.string.steam_session_sync_time_action),
                            onClick = onSyncSteamTime,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.steam_session_open_confirmations_action),
                            onClick = onOpenConfirmations,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_session_current_title),
                        description = stringResource(R.string.steam_session_current_description),
                    ) {
                        if (session == null) {
                            VaultInlineBanner(
                                text = stringResource(R.string.steam_session_current_none),
                                tone = VaultBannerTone.Warning,
                            )
                        } else {
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_steam_id),
                                value = session.steamId ?: stringResource(R.string.common_not_saved),
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_session_id),
                                value = maskValue(context, session.sessionId),
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_oauth_token),
                                value = maskValue(context, session.oauthToken),
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_cookies),
                                value = if (session.cookies.isEmpty()) {
                                    stringResource(R.string.steam_session_cookies_none)
                                } else {
                                    session.cookies.joinToString { it.name }
                                },
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_created_at),
                                value = session.createdAt,
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_updated_at),
                                value = session.updatedAt,
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_validation_status),
                                value = formatSessionValidationStatus(context, session.validationStatus),
                            )
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_last_validated),
                                value = session.lastValidatedAt
                                    ?: stringResource(R.string.steam_session_last_validated_none),
                            )
                            session.lastValidationErrorMessage?.let { errorMessage ->
                                VaultKeyValueRow(
                                    label = stringResource(R.string.steam_session_detail_last_validation_error),
                                    value = errorMessage,
                                )
                            }
                        }
                        VaultPrimaryButton(
                            text = stringResource(
                                if (isValidatingSession) {
                                    R.string.steam_session_validation_running
                                } else {
                                    R.string.steam_session_validation_action
                                },
                            ),
                            onClick = {
                                scope.launch {
                                    validationMessage = null
                                    validationError = null
                                    isValidatingSession = true
                                    try {
                                        val validatedSession = steamSessionValidationSyncManager
                                            .validateSession(tokenId)
                                        validationMessage = context.getString(
                                            R.string.steam_session_validation_success,
                                            validatedSession.steamId
                                                ?: context.getString(R.string.common_not_saved),
                                        )
                                        localRefreshVersion += 1
                                    } catch (error: Exception) {
                                        validationError = error.message
                                            ?: context.getString(R.string.steam_session_validation_failed)
                                        localRefreshVersion += 1
                                    } finally {
                                        isValidatingSession = false
                                    }
                                }
                            },
                            enabled = session != null && !isValidatingSession,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.steam_session_clear_action),
                            onClick = {
                                scope.launch {
                                    clearMessage = null
                                    clearError = null
                                    validationMessage = null
                                    validationError = null
                                    try {
                                        steamSessionRepository.clearSession(tokenId)
                                        clearMessage = context.getString(R.string.steam_session_clear_success)
                                        localRefreshVersion += 1
                                    } catch (error: Exception) {
                                        clearError = error.message
                                            ?: context.getString(R.string.steam_session_clear_failed)
                                    }
                                }
                            },
                            enabled = session != null,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.steam_session_open_confirmations_action),
                            onClick = onOpenConfirmations,
                            enabled = session != null,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_session_protocol_repair_title),
                        description = stringResource(R.string.steam_session_protocol_repair_description),
                    ) {
                        recoveryStatusMessage?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Success,
                            )
                        }
                        recoveryErrorMessage?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Error,
                            )
                        }
                        VaultTextField(
                            value = recoveryUsernameInput,
                            onValueChange = { recoveryUsernameInput = it },
                            label = stringResource(
                                R.string.steam_add_authenticator_protocol_username_label,
                            ),
                            enabled = !isRecoveringSession,
                            singleLine = true,
                        )
                        VaultTextField(
                            value = recoveryPasswordInput,
                            onValueChange = { recoveryPasswordInput = it },
                            label = stringResource(
                                R.string.steam_add_authenticator_protocol_password_label,
                            ),
                            enabled = !isRecoveringSession,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Text(
                            text = stringResource(R.string.steam_session_protocol_repair_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VaultPrimaryButton(
                            text = stringResource(
                                if (isRecoveringSession) {
                                    R.string.steam_session_protocol_repair_action_running
                                } else {
                                    R.string.steam_session_protocol_repair_action
                                },
                            ),
                            onClick = {
                                saveMessage = null
                                saveError = null
                                validationMessage = null
                                validationError = null
                                clearMessage = null
                                clearError = null
                                repairMessage = null
                                repairError = null
                                startProtocolRecovery(token = token, session = session)
                            },
                            enabled = !isRecoveringSession,
                        )
                        if (isRecoveringSession) {
                            VaultSecondaryButton(
                                text = stringResource(
                                    R.string.steam_add_authenticator_challenge_cancel_action,
                                ),
                                onClick = {
                                    cancelProtocolRecovery(
                                        context.getString(R.string.steam_session_protocol_repair_cancelled),
                                    )
                                },
                            )
                        }
                    }
                }
                pendingRecoveryChallenge?.let { challenge ->
                    item {
                        ScreenSectionCard(
                            title = protocolRecoveryChallengeTitle(context, challenge),
                            description = protocolRecoveryChallengeDescription(context, challenge),
                        ) {
                            when (challenge) {
                                is SteamProtocolLoginChallenge.EmailCode,
                                is SteamProtocolLoginChallenge.DeviceCode,
                                -> {
                                    VaultTextField(
                                        value = recoveryChallengeCodeInput,
                                        onValueChange = { recoveryChallengeCodeInput = it },
                                        label = stringResource(
                                            R.string.steam_add_authenticator_challenge_code_label,
                                        ),
                                        supportingText = protocolRecoveryChallengeSupportingText(
                                            context,
                                            challenge,
                                        ),
                                        singleLine = true,
                                    )
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            R.string.steam_add_authenticator_challenge_submit_action,
                                        ),
                                        onClick = {
                                            val trimmedCode = recoveryChallengeCodeInput.trim()
                                            if (trimmedCode.isEmpty()) {
                                                recoveryErrorMessage = context.getString(
                                                    R.string.steam_add_authenticator_challenge_code_required,
                                                )
                                                return@VaultPrimaryButton
                                            }
                                            recoveryErrorMessage = null
                                            submitRecoveryChallenge(
                                                SteamProtocolLoginChallengeAnswer.Code(trimmedCode),
                                            )
                                        },
                                    )
                                }

                                is SteamProtocolLoginChallenge.DeviceConfirmation -> {
                                    Text(
                                        text = stringResource(
                                            R.string.steam_add_authenticator_challenge_device_confirmation_note,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            R.string.steam_add_authenticator_challenge_device_confirmation_approved_action,
                                        ),
                                        onClick = {
                                            recoveryErrorMessage = null
                                            submitRecoveryChallenge(
                                                SteamProtocolLoginChallengeAnswer.DeviceConfirmation(
                                                    accepted = true,
                                                ),
                                            )
                                        },
                                    )
                                    VaultSecondaryButton(
                                        text = stringResource(
                                            R.string.steam_add_authenticator_challenge_cancel_action,
                                        ),
                                        onClick = {
                                            cancelProtocolRecovery(
                                                context.getString(
                                                    R.string.steam_session_protocol_repair_cancelled,
                                                ),
                                            )
                                        },
                                    )
                                }

                                is SteamProtocolLoginChallenge.QrCode -> Unit
                            }
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_session_mafile_repair_title),
                        description = stringResource(R.string.steam_session_mafile_repair_description),
                    ) {
                        repairMessage?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Success,
                            )
                        }
                        repairError?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Error,
                            )
                        }
                        VaultTextField(
                            value = repairPayloadInput,
                            onValueChange = { repairPayloadInput = it },
                            label = stringResource(R.string.steam_session_mafile_repair_payload_label),
                            supportingText = stringResource(
                                R.string.steam_session_mafile_repair_payload_supporting,
                            ),
                            minLines = 5,
                        )
                        VaultPrimaryButton(
                            text = stringResource(R.string.steam_session_mafile_repair_action),
                            onClick = {
                                scope.launch {
                                    repairMessage = null
                                    repairError = null
                                    saveMessage = null
                                    saveError = null
                                    validationMessage = null
                                    validationError = null
                                    try {
                                        val importDraft = importParser.parse(
                                            rawPayload = repairPayloadInput,
                                            manualAccountName = "",
                                            manualSharedSecret = "",
                                        )
                                        val importedSession = importDraft.importedSession
                                            ?: throw IllegalArgumentException(
                                                context.getString(
                                                    R.string.steam_session_mafile_repair_missing_session_sda,
                                                ),
                                            )
                                        if (!canRepairSessionFromImportDraft(token, importDraft)) {
                                            throw IllegalArgumentException(
                                                context.getString(
                                                    R.string.steam_session_mafile_repair_account_mismatch,
                                                    importDraft.accountName,
                                                    token.accountName,
                                                ),
                                            )
                                        }
                                        steamSessionRepository.saveSession(
                                            importedSession.toRecord(
                                                tokenId = tokenId,
                                                accountName = token.accountName,
                                                existingSession = session,
                                            ),
                                        )
                                        repairPayloadInput = ""
                                        repairMessage = context.getString(
                                            R.string.steam_session_mafile_repair_success,
                                            token.accountName,
                                        )
                                        localRefreshVersion += 1
                                    } catch (error: Exception) {
                                        repairError = error.message
                                            ?: context.getString(R.string.steam_session_save_failed)
                                    }
                                }
                            },
                            enabled = repairPayloadInput.isNotBlank(),
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_session_editor_title),
                        description = stringResource(
                            if (session == null) {
                                R.string.steam_session_editor_description_new
                            } else {
                                R.string.steam_session_editor_description_existing
                            },
                        ),
                    ) {
                        VaultTextField(
                            value = steamIdInput,
                            onValueChange = { steamIdInput = it },
                            label = stringResource(R.string.steam_session_editor_steam_id_label),
                            singleLine = true,
                        )
                        VaultTextField(
                            value = sessionIdInput,
                            onValueChange = { sessionIdInput = it },
                            label = stringResource(R.string.steam_session_editor_session_id_label),
                            singleLine = true,
                        )
                        VaultTextField(
                            value = oauthTokenInput,
                            onValueChange = { oauthTokenInput = it },
                            label = stringResource(R.string.steam_session_editor_oauth_token_label),
                            singleLine = true,
                        )
                        VaultTextField(
                            value = rawCookiesInput,
                            onValueChange = { rawCookiesInput = it },
                            label = stringResource(R.string.steam_session_editor_cookies_label),
                            supportingText = stringResource(
                                R.string.steam_session_editor_cookies_supporting,
                            ),
                            minLines = 4,
                        )
                        VaultPrimaryButton(
                            text = stringResource(R.string.steam_session_save_action),
                            onClick = {
                                scope.launch {
                                    saveMessage = null
                                    saveError = null
                                    validationMessage = null
                                    validationError = null
                                    try {
                                        val updatedSession = SteamSessionEditorParser.buildSessionRecord(
                                            tokenId = tokenId,
                                            accountName = token.accountName,
                                            existingSession = session,
                                            steamIdInput = steamIdInput,
                                            sessionIdInput = sessionIdInput,
                                            oauthTokenInput = oauthTokenInput,
                                            rawCookiesInput = rawCookiesInput,
                                        )
                                        steamSessionRepository.saveSession(updatedSession)
                                        saveMessage = context.getString(
                                            if (session == null) {
                                                R.string.steam_session_save_success_new
                                            } else {
                                                R.string.steam_session_save_success_update
                                            },
                                        )
                                        localRefreshVersion += 1
                                    } catch (error: SteamSessionEditorException) {
                                        saveError = sessionEditorErrorMessage(context, error)
                                    } catch (error: Exception) {
                                        saveError = error.message
                                            ?: context.getString(R.string.steam_session_save_failed)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class SteamSessionScreenState(
    val token: TokenRecord,
    val session: SteamSessionRecord?,
)

private fun formatOffset(
    context: android.content.Context,
    offsetSeconds: Long,
): String {
    return when {
        offsetSeconds > 0L -> context.getString(R.string.common_offset_positive, offsetSeconds)
        offsetSeconds < 0L -> context.getString(R.string.common_offset_negative, offsetSeconds)
        else -> context.getString(R.string.common_offset_zero)
    }
}

private fun maskValue(
    context: android.content.Context,
    value: String?,
): String {
    if (value.isNullOrBlank()) {
        return context.getString(R.string.common_not_saved)
    }
    if (value.length <= 6) {
        return context.getString(R.string.common_masked_value)
    }
    return context.getString(
        R.string.common_truncated_value,
        value.take(3),
        value.takeLast(3),
    )
}

private fun formatSessionValidationStatus(
    context: android.content.Context,
    status: SteamSessionValidationStatus,
): String {
    return when (status) {
        SteamSessionValidationStatus.UNKNOWN -> {
            context.getString(R.string.steam_session_validation_status_unknown)
        }

        SteamSessionValidationStatus.SUCCESS -> {
            context.getString(R.string.steam_session_validation_status_success)
        }

        SteamSessionValidationStatus.ERROR -> {
            context.getString(R.string.steam_session_validation_status_error)
        }
    }
}

private fun sessionEditorErrorMessage(
    context: android.content.Context,
    error: SteamSessionEditorException,
): String {
    return when (error.error) {
        SteamSessionEditorError.SESSION_MATERIAL_MISSING -> {
            context.getString(R.string.steam_session_editor_material_required)
        }

        SteamSessionEditorError.COOKIE_MALFORMED -> {
            context.getString(
                R.string.steam_session_editor_cookie_malformed,
                error.lineNumber ?: 0,
            )
        }

        SteamSessionEditorError.COOKIE_NAME_MISSING -> {
            context.getString(
                R.string.steam_session_editor_cookie_name_missing,
                error.lineNumber ?: 0,
            )
        }

        SteamSessionEditorError.COOKIE_VALUE_MISSING -> {
            context.getString(
                R.string.steam_session_editor_cookie_value_missing,
                error.lineNumber ?: 0,
            )
        }
    }
}

private fun protocolRecoveryChallengeTitle(
    context: android.content.Context,
    challenge: SteamProtocolLoginChallenge,
): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.EmailCode -> {
            context.getString(R.string.steam_add_authenticator_challenge_email_title)
        }

        is SteamProtocolLoginChallenge.DeviceCode -> {
            context.getString(R.string.steam_add_authenticator_challenge_device_code_title)
        }

        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
            context.getString(R.string.steam_add_authenticator_challenge_device_confirmation_title)
        }

        is SteamProtocolLoginChallenge.QrCode -> "QR"
    }
}

private fun protocolRecoveryChallengeDescription(
    context: android.content.Context,
    challenge: SteamProtocolLoginChallenge,
): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.EmailCode -> {
            context.getString(
                R.string.steam_add_authenticator_challenge_email_description,
                challenge.emailAddress,
            )
        }

        is SteamProtocolLoginChallenge.DeviceCode -> {
            context.getString(R.string.steam_add_authenticator_challenge_device_code_description)
        }

        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
            challenge.confirmationUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { confirmationUrl ->
                    context.getString(
                        R.string.steam_add_authenticator_challenge_device_confirmation_description_with_url,
                        confirmationUrl,
                    )
                }
                ?: context.getString(
                    R.string.steam_add_authenticator_challenge_device_confirmation_description,
                )
        }

        is SteamProtocolLoginChallenge.QrCode -> challenge.challengeUrl
    }
}

private fun protocolRecoveryChallengeSupportingText(
    context: android.content.Context,
    challenge: SteamProtocolLoginChallenge,
): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.EmailCode -> {
            context.getString(
                if (challenge.previousCodeWasIncorrect) {
                    R.string.steam_add_authenticator_challenge_email_retry_supporting
                } else {
                    R.string.steam_add_authenticator_challenge_email_supporting
                },
            )
        }

        is SteamProtocolLoginChallenge.DeviceCode -> {
            context.getString(
                if (challenge.previousCodeWasIncorrect) {
                    R.string.steam_add_authenticator_challenge_device_code_retry_supporting
                } else {
                    R.string.steam_add_authenticator_challenge_device_code_supporting
                },
            )
        }

        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
            context.getString(R.string.steam_add_authenticator_challenge_device_confirmation_note)
        }

        is SteamProtocolLoginChallenge.QrCode -> challenge.challengeUrl
    }
}

private fun millisUntilNextSteamCodeWindow(offsetSeconds: Long): Long {
    val currentEpochSeconds = (System.currentTimeMillis() / 1000L) + offsetSeconds
    val remainder = currentEpochSeconds % 30L
    val secondsUntilNextWindow = if (remainder == 0L) 30L else 30L - remainder
    return secondsUntilNextWindow * 1000L
}

private fun generateCurrentSteamGuardCode(
    token: TokenRecord,
    vaultCryptography: VaultCryptography,
    offsetSeconds: Long,
): String {
    val secretBytes = SteamSecretCodec.decode(token.sharedSecret)
    return try {
        val currentEpochSeconds = (System.currentTimeMillis() / 1000L) + offsetSeconds
        vaultCryptography.generateSteamGuardCode(secretBytes, currentEpochSeconds)
    } finally {
        secretBytes.fill(0)
    }
}

private fun canRepairSessionFromImportDraft(
    token: TokenRecord,
    importDraft: com.example.steam_vault_app.domain.model.ImportDraft,
): Boolean {
    return matchesImportValue(token.identitySecret, importDraft.identitySecret) ||
        matchesImportValue(token.deviceId, importDraft.deviceId) ||
        matchesImportValue(token.tokenGid, importDraft.tokenGid) ||
        matchesImportValue(token.accountName, importDraft.accountName)
}

private fun matchesImportValue(
    currentValue: String?,
    importedValue: String?,
): Boolean {
    val normalizedCurrentValue = currentValue?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val normalizedImportedValue = importedValue?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return normalizedCurrentValue.equals(normalizedImportedValue, ignoreCase = true)
}
