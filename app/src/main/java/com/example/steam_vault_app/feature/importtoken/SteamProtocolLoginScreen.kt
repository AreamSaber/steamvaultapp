package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallenge
import com.example.steam_vault_app.domain.model.SteamProtocolLoginChallengeAnswer
import com.example.steam_vault_app.domain.model.SteamProtocolLoginMode
import com.example.steam_vault_app.domain.model.SteamProtocolLoginRequest
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultTextField
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SteamProtocolLoginScreen(
    bindingContextRepository: SteamAuthenticatorBindingContextRepository,
    enrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    onOpenBindingPreparation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    DisposableEffect(Unit) {
        onDispose {
            protocolLoginJob?.cancel()
            pendingChallengeResponse?.complete(SteamProtocolLoginChallengeAnswer.Cancelled)
            pendingChallengeResponse = null
        }
    }

    fun submitChallenge(answer: SteamProtocolLoginChallengeAnswer) {
        val deferred = pendingChallengeResponse ?: return
        pendingChallengeResponse = null
        pendingChallenge = null
        challengeCodeInput = ""
        deferred.complete(answer)
    }

    suspend fun persistProtocolBindingContext(
        loginResult: com.example.steam_vault_app.domain.model.SteamProtocolLoginResult,
    ) {
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
        enrollmentDraftRepository.clearDraft()

        activeQrChallenge = null
        protocolStatusMessage = context.getString(
            R.string.steam_add_authenticator_protocol_success,
            bindingContext.accountName,
        )
        protocolErrorMessage = null
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
        if (
            mode != SteamProtocolLoginMode.QR_CODE &&
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
                                val deferred = CompletableDeferred<SteamProtocolLoginChallengeAnswer>()
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.steam_add_authenticator_protocol_title),
                subtitle = stringResource(R.string.steam_add_authenticator_protocol_description),
            )
        }

        protocolStatusMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Success,
                )
            }
        }
        protocolErrorMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Error,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_protocol_login_modern_steps_title),
                description = stringResource(R.string.steam_protocol_login_modern_steps_body),
            ) {
                VaultInlineBanner(
                    text = stringResource(R.string.vault_security_note_body),
                    tone = VaultBannerTone.Neutral,
                )
            }
        }

        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_add_authenticator_protocol_title),
                description = stringResource(R.string.steam_add_authenticator_protocol_description),
            ) {
                VaultTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = stringResource(R.string.steam_add_authenticator_protocol_username_label),
                    enabled = !isProtocolLoggingIn,
                    singleLine = true,
                )
                VaultTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = stringResource(R.string.steam_add_authenticator_protocol_password_label),
                    enabled = !isProtocolLoggingIn,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                VaultPrimaryButton(
                    text = stringResource(
                        if (isProtocolLoggingIn) {
                            R.string.steam_add_authenticator_protocol_action_loading
                        } else {
                            R.string.steam_add_authenticator_protocol_action
                        },
                    ),
                    onClick = { startProtocolLogin(SteamProtocolLoginMode.INITIAL) },
                    enabled = !isProtocolLoggingIn,
                )
                VaultSecondaryButton(
                    text = stringResource(
                        if (isProtocolLoggingIn && activeQrChallenge != null) {
                            R.string.steam_add_authenticator_protocol_qr_action_loading
                        } else {
                            R.string.steam_add_authenticator_protocol_qr_action
                        },
                    ),
                    onClick = { startProtocolLogin(SteamProtocolLoginMode.QR_CODE) },
                    enabled = !isProtocolLoggingIn,
                )
            }
        }

        activeQrChallenge?.let { qrChallenge ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_add_authenticator_protocol_qr_preview_title),
                    description = stringResource(R.string.steam_add_authenticator_protocol_qr_preview_description),
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
                    if (qrChallenge.hadRemoteInteraction) {
                        VaultInlineBanner(
                            text = stringResource(R.string.steam_add_authenticator_protocol_qr_interacted),
                            tone = VaultBannerTone.Success,
                        )
                    }
                    VaultSecondaryButton(
                        text = stringResource(
                            R.string.steam_add_authenticator_protocol_qr_cancel_action,
                        ),
                        onClick = {
                            cancelProtocolLogin(
                                context.getString(R.string.steam_add_authenticator_protocol_cancelled),
                            )
                        },
                    )
                }
            }
        }

        pendingChallenge?.let { challenge ->
            item {
                ScreenSectionCard(
                    title = challengeTitle(challenge),
                    description = challengeDescription(challenge),
                ) {
                    if (
                        challenge is SteamProtocolLoginChallenge.EmailCode ||
                        challenge is SteamProtocolLoginChallenge.DeviceCode
                    ) {
                        VaultTextField(
                            value = challengeCodeInput,
                            onValueChange = { challengeCodeInput = it },
                            label = stringResource(R.string.steam_add_authenticator_challenge_code_label),
                            supportingText = challengeSupportingText(challenge),
                            singleLine = true,
                        )
                        VaultPrimaryButton(
                            text = stringResource(
                                R.string.steam_add_authenticator_challenge_submit_action,
                            ),
                            onClick = {
                                val trimmedCode = challengeCodeInput.trim()
                                if (trimmedCode.isEmpty()) {
                                    protocolErrorMessage = context.getString(
                                        R.string.steam_add_authenticator_challenge_code_required,
                                    )
                                    return@VaultPrimaryButton
                                }
                                protocolErrorMessage = null
                                submitChallenge(SteamProtocolLoginChallengeAnswer.Code(trimmedCode))
                            },
                        )
                    } else if (challenge is SteamProtocolLoginChallenge.DeviceConfirmation) {
                        VaultPrimaryButton(
                            text = stringResource(
                                R.string.steam_add_authenticator_challenge_device_confirmation_approved_action,
                            ),
                            onClick = {
                                protocolErrorMessage = null
                                submitChallenge(
                                    SteamProtocolLoginChallengeAnswer.DeviceConfirmation(
                                        accepted = true,
                                    ),
                                )
                            },
                        )
                        VaultSecondaryButton(
                            text = stringResource(
                                R.string.steam_add_authenticator_challenge_device_confirmation_fallback_action,
                            ),
                            onClick = {
                                protocolErrorMessage = null
                                submitChallenge(
                                    SteamProtocolLoginChallengeAnswer.DeviceConfirmation(
                                        accepted = false,
                                    ),
                                )
                            },
                        )
                    } else {
                        VaultInlineBanner(
                            text = stringResource(R.string.steam_add_authenticator_protocol_qr_waiting),
                            tone = VaultBannerTone.Neutral,
                        )
                    }
                    VaultSecondaryButton(
                        text = stringResource(R.string.steam_add_authenticator_challenge_cancel_action),
                        onClick = {
                            protocolStatusMessage = null
                            submitChallenge(SteamProtocolLoginChallengeAnswer.Cancelled)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun challengeTitle(challenge: SteamProtocolLoginChallenge): String {
    return stringResource(
        when (challenge) {
            is SteamProtocolLoginChallenge.QrCode -> R.string.steam_add_authenticator_protocol_qr_preview_title
            is SteamProtocolLoginChallenge.EmailCode -> R.string.steam_add_authenticator_challenge_email_title
            is SteamProtocolLoginChallenge.DeviceCode -> R.string.steam_add_authenticator_challenge_device_code_title
            is SteamProtocolLoginChallenge.DeviceConfirmation -> R.string.steam_add_authenticator_challenge_device_confirmation_title
        },
    )
}

@Composable
private fun challengeDescription(challenge: SteamProtocolLoginChallenge): String {
    return when (challenge) {
        is SteamProtocolLoginChallenge.QrCode -> stringResource(R.string.steam_add_authenticator_protocol_qr_preview_description)
        is SteamProtocolLoginChallenge.EmailCode -> stringResource(R.string.steam_add_authenticator_challenge_email_description, challenge.emailAddress)
        is SteamProtocolLoginChallenge.DeviceCode -> stringResource(R.string.steam_add_authenticator_challenge_device_code_description)
        is SteamProtocolLoginChallenge.DeviceConfirmation -> {
            challenge.confirmationUrl?.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.steam_add_authenticator_challenge_device_confirmation_description_with_url, it)
            } ?: stringResource(R.string.steam_add_authenticator_challenge_device_confirmation_description)
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
