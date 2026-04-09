package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginMode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SteamAddAuthenticatorScreen(
    bindingContextRepository: SteamAuthenticatorBindingContextRepository,
    enrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    onOpenBindingPreparation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browserReturn by SteamExternalBrowserLoginManager.browserReturn.collectAsState()

    var usernameInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var protocolStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var protocolErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isProtocolLoggingIn by rememberSaveable { mutableStateOf(false) }
    var activeQrChallenge by remember { mutableStateOf<SteamProtocolLoginChallenge.QrCode?>(null) }
    var protocolLoginJob by remember { mutableStateOf<Job?>(null) }
    var pendingChallenge by remember { mutableStateOf<SteamProtocolLoginChallenge?>(null) }
    var pendingChallengeResponse by remember {
        mutableStateOf<CompletableDeferred<SteamProtocolLoginChallengeAnswer>?>(null)
    }
    var challengeCodeInput by rememberSaveable { mutableStateOf("") }

    var steamIdInput by rememberSaveable { mutableStateOf("") }
    var sessionIdInput by rememberSaveable { mutableStateOf("") }
    var oauthTokenInput by rememberSaveable { mutableStateOf("") }
    var currentUrlInput by rememberSaveable { mutableStateOf("") }
    var rawCookiesInput by rememberSaveable { mutableStateOf("") }
    var capturedAtInput by rememberSaveable { mutableStateOf("") }
    var storedBindingContext by remember { mutableStateOf<SteamAuthenticatorBindingContext?>(null) }
    var storedDraft by remember { mutableStateOf<SteamAuthenticatorEnrollmentDraft?>(null) }
    var storedDraftError by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPersistedDraftSignature by rememberSaveable { mutableStateOf("") }

    val activeDraft = remember(
        steamIdInput,
        sessionIdInput,
        oauthTokenInput,
        currentUrlInput,
        rawCookiesInput,
        capturedAtInput,
        storedDraft?.webApiKey,
    ) {
        buildEnrollmentDraft(
            steamIdInput = steamIdInput,
            sessionIdInput = sessionIdInput,
            oauthTokenInput = oauthTokenInput,
            currentUrlInput = currentUrlInput,
            rawCookiesInput = rawCookiesInput,
            capturedAtInput = capturedAtInput,
            webApiKey = storedDraft?.webApiKey,
        )
    }
    val effectiveDraft = activeDraft ?: storedDraft
    val bindingPreparation = effectiveDraft?.let { draft ->
        runCatching { SteamAuthenticatorBindingPreparationFactory.from(draft) }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose {
            protocolLoginJob?.cancel()
            pendingChallengeResponse?.complete(SteamProtocolLoginChallengeAnswer.Cancelled)
            pendingChallengeResponse = null
        }
    }

    LaunchedEffect(bindingContextRepository, enrollmentDraftRepository) {
        storedDraftError = null
        storedBindingContext = try {
            bindingContextRepository.getContext()
        } catch (error: Exception) {
            storedDraftError = error.message
            null
        }
        storedDraft = try {
            enrollmentDraftRepository.getDraft()
        } catch (error: Exception) {
            storedDraftError = error.message
            null
        }
    }

    LaunchedEffect(browserReturn) {
        val result = browserReturn ?: return@LaunchedEffect
        currentUrlInput = result.uri.toString()
        capturedAtInput = result.receivedAt
        statusMessage = context.getString(
            R.string.steam_add_authenticator_browser_return_received,
            result.uri.toString(),
        )
        errorMessage = null
        SteamExternalBrowserLoginManager.clearReturn()
    }

    fun submitChallenge(answer: SteamProtocolLoginChallengeAnswer) {
        val deferred = pendingChallengeResponse ?: return
        pendingChallengeResponse = null
        pendingChallenge = null
        challengeCodeInput = ""
        deferred.complete(answer)
    }

    suspend fun persistProtocolBindingContext(loginResult: com.example.steam_vault_app.domain.model.SteamProtocolLoginResult) {
        val resolvedAccountName = loginResult.accountNameHint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: loginResult.session.steamId
        val bindingContext = SteamAuthenticatorBindingContext(
            accountName = resolvedAccountName,
            session = loginResult.session,
            capturedAt = Instant.now().toString(),
        )
        bindingContextRepository.saveContext(bindingContext)
        storedBindingContext = bindingContext
        enrollmentDraftRepository.clearDraft()
        storedDraft = null
        lastPersistedDraftSignature = ""
        activeQrChallenge = null
        protocolStatusMessage = context.getString(
            R.string.steam_add_authenticator_protocol_success,
            bindingContext.accountName,
        )
        statusMessage = null
        errorMessage = null
        onOpenBindingPreparation()
    }

    fun cancelProtocolLogin(cancelMessage: String? = null) {
        protocolLoginJob?.cancel()
        protocolLoginJob = null
        activeQrChallenge = null
        pendingChallengeResponse?.complete(SteamProtocolLoginChallengeAnswer.Cancelled)
        pendingChallengeResponse = null
        pendingChallenge = null
        challengeCodeInput = ""
        isProtocolLoggingIn = false
        protocolErrorMessage = null
        protocolStatusMessage = cancelMessage
    }

    fun startProtocolLogin(mode: SteamProtocolLoginMode) {
        if (mode != SteamProtocolLoginMode.QR_CODE &&
            (usernameInput.trim().isEmpty() || passwordInput.isEmpty())
        ) {
            protocolErrorMessage = context.getString(
                R.string.steam_add_authenticator_protocol_missing_credentials,
            )
            protocolStatusMessage = null
            return
        }

        protocolLoginJob?.cancel()
        val job = scope.launch {
            isProtocolLoggingIn = true
            activeQrChallenge = null
            pendingChallenge = null
            pendingChallengeResponse = null
            challengeCodeInput = ""
            protocolErrorMessage = null
            protocolStatusMessage = context.getString(
                if (mode == SteamProtocolLoginMode.QR_CODE) {
                    R.string.steam_add_authenticator_protocol_qr_started
                } else {
                    R.string.steam_add_authenticator_protocol_started
                },
            )
            try {
                val loginResult = steamProtocolLoginOrchestrator.login(
                    request = SteamProtocolLoginRequest(
                        username = usernameInput.trim(),
                        password = if (mode == SteamProtocolLoginMode.QR_CODE) "" else passwordInput,
                        mode = mode,
                    ),
                    respondToChallenge = { challenge ->
                        when (challenge) {
                            is SteamProtocolLoginChallenge.QrCode -> {
                                activeQrChallenge = challenge
                                protocolStatusMessage = context.getString(
                                    if (challenge.hadRemoteInteraction) {
                                        R.string.steam_add_authenticator_protocol_qr_interacted
                                    } else if (challenge.refreshed) {
                                        R.string.steam_add_authenticator_protocol_qr_refreshed
                                    } else {
                                        R.string.steam_add_authenticator_protocol_qr_waiting
                                    },
                                )
                                SteamProtocolLoginChallengeAnswer.QrCodeReady
                            }

                            else -> {
                                challengeCodeInput = ""
                                pendingChallenge = challenge
                                val deferred =
                                    CompletableDeferred<SteamProtocolLoginChallengeAnswer>()
                                pendingChallengeResponse = deferred
                                try {
                                    deferred.await()
                                } finally {
                                    if (pendingChallengeResponse === deferred) {
                                        pendingChallengeResponse = null
                                    }
                                    if (pendingChallenge == challenge) {
                                        pendingChallenge = null
                                    }
                                    challengeCodeInput = ""
                                }
                            }
                        }
                    },
                    onQrChallengeChanged = { qrChallenge ->
                        activeQrChallenge = qrChallenge
                        protocolStatusMessage = context.getString(
                            if (qrChallenge.hadRemoteInteraction) {
                                R.string.steam_add_authenticator_protocol_qr_interacted
                            } else if (qrChallenge.refreshed) {
                                R.string.steam_add_authenticator_protocol_qr_refreshed
                            } else {
                                R.string.steam_add_authenticator_protocol_qr_waiting
                            },
                        )
                    },
                )
                persistProtocolBindingContext(loginResult)
            } catch (_: CancellationException) {
                cancelProtocolLogin(
                    context.getString(R.string.steam_add_authenticator_protocol_cancelled),
                )
            } catch (error: Exception) {
                activeQrChallenge = null
                protocolErrorMessage = error.message
                    ?: context.getString(
                        R.string.steam_add_authenticator_protocol_failed,
                    )
            } finally {
                isProtocolLoggingIn = false
                if (protocolLoginJob === coroutineContext[Job]) {
                    protocolLoginJob = null
                }
            }
        }
        protocolLoginJob = job
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.steam_add_authenticator_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_add_authenticator_overview_title),
                description = stringResource(R.string.steam_add_authenticator_overview_description),
            ) {
                ChecklistRow(
                    label = stringResource(R.string.steam_add_authenticator_step_sign_in),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.steam_add_authenticator_step_verify),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.steam_add_authenticator_step_bind),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.steam_add_authenticator_step_complete),
                    highlighted = true,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_add_authenticator_protocol_title),
                description = stringResource(R.string.steam_add_authenticator_protocol_description),
            ) {
                protocolStatusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                protocolErrorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.steam_add_authenticator_protocol_username_label)) },
                    enabled = !isProtocolLoggingIn,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.steam_add_authenticator_protocol_password_label)) },
                    enabled = !isProtocolLoggingIn,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(
                    text = stringResource(R.string.steam_add_authenticator_protocol_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.steam_add_authenticator_protocol_qr_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Button(
                    onClick = { startProtocolLogin(SteamProtocolLoginMode.INITIAL) },
                    enabled = !isProtocolLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isProtocolLoggingIn) {
                                R.string.steam_add_authenticator_protocol_action_loading
                            } else {
                                R.string.steam_add_authenticator_protocol_action
                            },
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { startProtocolLogin(SteamProtocolLoginMode.QR_CODE) },
                    enabled = !isProtocolLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isProtocolLoggingIn && activeQrChallenge != null) {
                                R.string.steam_add_authenticator_protocol_qr_action_loading
                            } else {
                                R.string.steam_add_authenticator_protocol_qr_action
                            },
                        ),
                    )
                }
            }
        }
        activeQrChallenge?.let { qrChallenge ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_protocol_qr_preview_title),
                    description = stringResource(
                        R.string.steam_add_authenticator_protocol_qr_preview_description,
                    ),
                ) {
                    val qrBitmap = remember(qrChallenge.challengeUrl) {
                        runCatching {
                            SteamQrCodeBitmapGenerator.generate(qrChallenge.challengeUrl)
                        }.getOrNull()
                    }
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(
                                R.string.steam_add_authenticator_protocol_qr_preview_title,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_protocol_qr_url,
                            qrChallenge.challengeUrl,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (qrChallenge.hadRemoteInteraction) {
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_protocol_qr_interacted,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            cancelProtocolLogin(
                                context.getString(
                                    R.string.steam_add_authenticator_protocol_cancelled,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_protocol_qr_cancel_action,
                            ),
                        )
                    }
                }
            }
        }
        pendingChallenge?.let { challenge ->
            item {
                ScreenSectionCard(
                    title = challengeTitle(challenge),
                    description = challengeDescription(challenge),
                ) {
                    if (challenge is SteamProtocolLoginChallenge.EmailCode ||
                        challenge is SteamProtocolLoginChallenge.DeviceCode
                    ) {
                        OutlinedTextField(
                            value = challengeCodeInput,
                            onValueChange = { challengeCodeInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(
                                        R.string.steam_add_authenticator_challenge_code_label,
                                    ),
                                )
                            },
                            supportingText = {
                                Text(challengeSupportingText(challenge))
                            },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val trimmedCode = challengeCodeInput.trim()
                                if (trimmedCode.isEmpty()) {
                                    protocolErrorMessage = context.getString(
                                        R.string.steam_add_authenticator_challenge_code_required,
                                    )
                                    return@Button
                                }
                                protocolErrorMessage = null
                                submitChallenge(SteamProtocolLoginChallengeAnswer.Code(trimmedCode))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.steam_add_authenticator_challenge_submit_action,
                                ),
                            )
                        }
                    } else if (challenge is SteamProtocolLoginChallenge.DeviceConfirmation) {
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_challenge_device_confirmation_note,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                protocolErrorMessage = null
                                submitChallenge(
                                    SteamProtocolLoginChallengeAnswer.DeviceConfirmation(
                                        accepted = true,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.steam_add_authenticator_challenge_device_confirmation_approved_action,
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                protocolErrorMessage = null
                                submitChallenge(
                                    SteamProtocolLoginChallengeAnswer.DeviceConfirmation(
                                        accepted = false,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.steam_add_authenticator_challenge_device_confirmation_fallback_action,
                                ),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_protocol_qr_waiting,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            protocolStatusMessage = null
                            submitChallenge(SteamProtocolLoginChallengeAnswer.Cancelled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_challenge_cancel_action,
                            ),
                        )
                    }
                }
            }
        }
        storedBindingContext?.let { bindingContext ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_protocol_saved_title),
                    description = stringResource(
                        R.string.steam_add_authenticator_protocol_saved_description,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_protocol_saved_account_name,
                            bindingContext.accountName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_steam_id,
                            bindingContext.session.steamId,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    bindingContext.session.sessionId?.let { sessionId ->
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_saved_draft_session_id,
                                maskSensitiveValue(context, sessionId),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_captured_at,
                            bindingContext.capturedAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_protocol_saved_cookie_note,
                            bindingContext.session.cookies.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenBindingPreparation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_protocol_saved_continue_action,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    bindingContextRepository.clearContext()
                                    storedBindingContext = null
                                    protocolStatusMessage = context.getString(
                                        R.string.steam_add_authenticator_protocol_saved_cleared,
                                    )
                                    protocolErrorMessage = null
                                } catch (error: Exception) {
                                    protocolErrorMessage = error.message
                                        ?: context.getString(
                                            R.string.steam_add_authenticator_protocol_saved_clear_failed,
                                        )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_protocol_saved_clear_action,
                            ),
                        )
                    }
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_add_authenticator_browser_title),
                description = stringResource(R.string.steam_add_authenticator_browser_description),
            ) {
                Text(
                    text = stringResource(
                        R.string.steam_add_authenticator_browser_login_url,
                        SteamExternalBrowserLoginManager.loginUri.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.steam_add_authenticator_browser_callback_url,
                        SteamExternalBrowserLoginManager.callbackUri.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.steam_add_authenticator_browser_separation_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                storedDraftError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        SteamExternalBrowserLoginManager.openLogin(context)
                        statusMessage = context.getString(
                            R.string.steam_add_authenticator_browser_opened,
                        )
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.steam_add_authenticator_browser_open_action))
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_add_authenticator_manual_title),
                description = stringResource(R.string.steam_add_authenticator_manual_description),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = steamIdInput,
                        onValueChange = { steamIdInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.steam_session_editor_steam_id_label)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = sessionIdInput,
                        onValueChange = { sessionIdInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.steam_session_editor_session_id_label)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = oauthTokenInput,
                        onValueChange = { oauthTokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.steam_session_editor_oauth_token_label)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = currentUrlInput,
                        onValueChange = { currentUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.steam_add_authenticator_manual_current_url_label))
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = rawCookiesInput,
                        onValueChange = { rawCookiesInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.steam_session_editor_cookies_label)) },
                        supportingText = {
                            Text(stringResource(R.string.steam_session_editor_cookies_supporting))
                        },
                        minLines = 4,
                    )
                    Button(
                        onClick = {
                            val draftToSave = activeDraft
                            if (draftToSave == null) {
                                errorMessage = context.getString(
                                    R.string.steam_add_authenticator_manual_required,
                                )
                                statusMessage = null
                                return@Button
                            }
                            scope.launch {
                                try {
                                    enrollmentDraftRepository.saveDraft(draftToSave)
                                    storedDraft = draftToSave
                                    lastPersistedDraftSignature = draftToSave.signature
                                    storedDraftError = null
                                    statusMessage = context.getString(
                                        R.string.steam_add_authenticator_saved_draft_saved,
                                    )
                                    errorMessage = null
                                } catch (error: Exception) {
                                    errorMessage = error.message
                                        ?: context.getString(
                                            R.string.steam_add_authenticator_saved_draft_save_failed,
                                        )
                                    statusMessage = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_add_authenticator_manual_save_action))
                    }
                    OutlinedButton(
                        onClick = {
                            steamIdInput = ""
                            sessionIdInput = ""
                            oauthTokenInput = ""
                            currentUrlInput = ""
                            rawCookiesInput = ""
                            capturedAtInput = ""
                            statusMessage = null
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_add_authenticator_manual_clear_action))
                    }
                }
            }
        }
        storedDraft?.let { draft ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_saved_draft_title),
                    description = stringResource(
                        R.string.steam_add_authenticator_saved_draft_description,
                    ),
                ) {
                    draft.steamId?.let { steamId ->
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_saved_draft_steam_id,
                                steamId,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_session_id,
                            maskSensitiveValue(context, draft.sessionId),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_captured_at,
                            draft.capturedAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_source_url,
                            draft.currentUrl,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_cookie_note,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Button(
                        onClick = {
                            steamIdInput = draft.steamId.orEmpty()
                            sessionIdInput = draft.sessionId
                            oauthTokenInput = draft.oauthToken.orEmpty()
                            currentUrlInput = draft.currentUrl
                            rawCookiesInput = draft.cookiesText
                            capturedAtInput = draft.capturedAt
                            lastPersistedDraftSignature = draft.signature
                            errorMessage = null
                            statusMessage = context.getString(
                                R.string.steam_add_authenticator_saved_draft_restored,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_saved_draft_load_action,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    enrollmentDraftRepository.clearDraft()
                                    storedDraft = null
                                    if (lastPersistedDraftSignature == draft.signature) {
                                        lastPersistedDraftSignature = ""
                                    }
                                    storedDraftError = null
                                    errorMessage = null
                                    statusMessage = context.getString(
                                        R.string.steam_add_authenticator_saved_draft_cleared,
                                    )
                                } catch (error: Exception) {
                                    errorMessage = error.message
                                        ?: context.getString(
                                            R.string.steam_add_authenticator_saved_draft_clear_failed,
                                        )
                                    statusMessage = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_saved_draft_clear_action,
                            ),
                        )
                    }
                }
            }
        }
        activeDraft?.let { draft ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_session_title),
                    description = stringResource(R.string.steam_add_authenticator_session_description),
                ) {
                    draft.steamId?.let { steamId ->
                        Text(
                            text = stringResource(
                                R.string.steam_add_authenticator_session_steam_id,
                                steamId,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_session_session_id,
                            maskSensitiveValue(context, draft.sessionId),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_saved_draft_captured_at,
                            draft.capturedAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_add_authenticator_session_current_url,
                            draft.currentUrl,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.steam_add_authenticator_session_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        effectiveDraft?.let { draft ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_binding_ready_title),
                    description = stringResource(
                        R.string.steam_add_authenticator_binding_ready_description,
                    ),
                ) {
                    ChecklistRow(
                        label = if (!draft.sessionId.isBlank()) {
                            stringResource(
                                R.string.steam_add_authenticator_binding_ready_check_session,
                            )
                        } else {
                            stringResource(
                                R.string.steam_authenticator_binding_check_not_ready,
                            )
                        },
                        highlighted = !draft.sessionId.isBlank(),
                    )
                    bindingPreparation?.let { preparation ->
                        ChecklistRow(
                            label = stringResource(
                                if (preparation.sessionIdCookie != null) {
                                    R.string.steam_authenticator_binding_check_session_id
                                } else {
                                    R.string.steam_authenticator_binding_check_not_ready
                                },
                            ),
                            highlighted = preparation.sessionIdCookie != null,
                        )
                        ChecklistRow(
                            label = stringResource(
                                if (preparation.steamLoginSecureCookie != null) {
                                    R.string.steam_authenticator_binding_check_steam_login_secure
                                } else {
                                    R.string.steam_authenticator_binding_check_not_ready
                                },
                            ),
                            highlighted = preparation.steamLoginSecureCookie != null,
                        )
                        ChecklistRow(
                            label = stringResource(
                                if (preparation.oauthToken != null || preparation.webApiKey != null) {
                                    R.string.steam_authenticator_binding_check_oauth_ready
                                } else {
                                    R.string.steam_authenticator_binding_check_auth_missing
                                },
                            ),
                            highlighted = preparation.oauthToken != null || preparation.webApiKey != null,
                        )
                    }
                    ChecklistRow(
                        label = stringResource(
                            if (storedDraft?.signature == draft.signature) {
                                R.string.steam_add_authenticator_binding_ready_check_persisted
                            } else {
                                R.string.steam_add_authenticator_binding_ready_check_persist_required
                            },
                        ),
                        highlighted = true,
                    )
                    ChecklistRow(
                        label = stringResource(
                            R.string.steam_add_authenticator_binding_ready_check_next_step,
                        ),
                        highlighted = true,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val draftToOpen = activeDraft ?: storedDraft ?: return@launch
                                if (storedDraft?.signature != draftToOpen.signature) {
                                    try {
                                        enrollmentDraftRepository.saveDraft(draftToOpen)
                                        storedDraft = draftToOpen
                                        lastPersistedDraftSignature = draftToOpen.signature
                                        storedDraftError = null
                                    } catch (error: Exception) {
                                        errorMessage = error.message
                                            ?: context.getString(
                                                R.string.steam_add_authenticator_saved_draft_save_failed,
                                            )
                                        statusMessage = null
                                        return@launch
                                    }
                                }
                                bindingContextRepository.clearContext()
                                storedBindingContext = null
                                onOpenBindingPreparation()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.steam_add_authenticator_binding_open_action,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun buildEnrollmentDraft(
    steamIdInput: String,
    sessionIdInput: String,
    oauthTokenInput: String,
    currentUrlInput: String,
    rawCookiesInput: String,
    capturedAtInput: String,
    webApiKey: String?,
): SteamAuthenticatorEnrollmentDraft? {
    val trimmedSteamId = steamIdInput.trim().takeIf { it.isNotBlank() }
    val trimmedSessionId = sessionIdInput.trim()
    val trimmedOauthToken = oauthTokenInput.trim().takeIf { it.isNotBlank() }
    val trimmedCurrentUrl = currentUrlInput.trim()
    val trimmedCookies = rawCookiesInput.trim()
    if (
        trimmedSteamId == null &&
        trimmedSessionId.isBlank() &&
        trimmedOauthToken == null &&
        trimmedCurrentUrl.isBlank() &&
        trimmedCookies.isBlank()
    ) {
        return null
    }
    return SteamAuthenticatorEnrollmentDraft(
        steamId = trimmedSteamId,
        sessionId = trimmedSessionId,
        cookiesText = trimmedCookies,
        currentUrl = trimmedCurrentUrl.ifBlank { SteamExternalBrowserLoginManager.loginUri.toString() },
        capturedAt = capturedAtInput.ifBlank { Instant.now().toString() },
        oauthToken = trimmedOauthToken,
        webApiKey = webApiKey,
    )
}

@Composable
private fun challengeTitle(challenge: SteamProtocolLoginChallenge): String {
    return stringResource(
        when (challenge) {
            is SteamProtocolLoginChallenge.QrCode -> {
                R.string.steam_add_authenticator_protocol_qr_preview_title
            }

            is SteamProtocolLoginChallenge.EmailCode -> {
                R.string.steam_add_authenticator_challenge_email_title
            }

            is SteamProtocolLoginChallenge.DeviceCode -> {
                R.string.steam_add_authenticator_challenge_device_code_title
            }

            is SteamProtocolLoginChallenge.DeviceConfirmation -> {
                R.string.steam_add_authenticator_challenge_device_confirmation_title
            }
        },
    )
}

@Composable
private fun challengeDescription(challenge: SteamProtocolLoginChallenge): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.QrCode -> {
            stringResource(
                R.string.steam_add_authenticator_protocol_qr_preview_description,
            )
        }

        is SteamProtocolLoginChallenge.EmailCode -> {
            stringResource(
                R.string.steam_add_authenticator_challenge_email_description,
                challenge.emailAddress,
            )
        }

        is SteamProtocolLoginChallenge.DeviceCode -> {
            stringResource(
                R.string.steam_add_authenticator_challenge_device_code_description,
            )
        }

        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
            challenge.confirmationUrl
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    stringResource(
                        R.string.steam_add_authenticator_challenge_device_confirmation_description_with_url,
                        it,
                    )
                }
                ?: stringResource(
                    R.string.steam_add_authenticator_challenge_device_confirmation_description,
                )
        }
    }
}

@Composable
private fun challengeSupportingText(challenge: SteamProtocolLoginChallenge): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.QrCode -> ""

        is SteamProtocolLoginChallenge.EmailCode -> {
            stringResource(
                if (challenge.previousCodeWasIncorrect) {
                    R.string.steam_add_authenticator_challenge_email_retry_supporting
                } else {
                    R.string.steam_add_authenticator_challenge_email_supporting
                },
            )
        }

        is SteamProtocolLoginChallenge.DeviceCode -> {
            stringResource(
                if (challenge.previousCodeWasIncorrect) {
                    R.string.steam_add_authenticator_challenge_device_code_retry_supporting
                } else {
                    R.string.steam_add_authenticator_challenge_device_code_supporting
                },
            )
        }

        is SteamProtocolLoginChallenge.DeviceConfirmation -> ""
    }
}

private fun maskSensitiveValue(
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
