package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.security.SteamSecretCodec
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginRequest
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBeginResult
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingApiClient
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingException
import com.example.steam_vault_app.data.steam.SteamAuthenticatorBindingFailureReason
import com.example.steam_vault_app.data.steam.SteamAuthenticatorFinalizeRequest
import com.example.steam_vault_app.data.steam.SteamAuthenticatorPhoneAccessRequest
import com.example.steam_vault_app.data.steam.SteamAuthenticatorSetPhoneNumberRequest
import com.example.steam_vault_app.data.steam.SteamAuthenticatorVerifyPhoneCodeRequest
import com.example.steam_vault_app.domain.auth.SteamProtocolLoginOrchestrator
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressDraft
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingProgressStage
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamSessionValidationStatus
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingProgressRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultKeyValueRow
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultProgressSteps
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultSensitiveValueRow
import com.example.steam_vault_app.ui.common.VaultStepItem
import com.example.steam_vault_app.ui.common.VaultStepState
import com.example.steam_vault_app.ui.common.VaultTextField
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SteamAuthenticatorBindingScreen(
    bindingContextRepository: SteamAuthenticatorBindingContextRepository,
    bindingProgressRepository: SteamAuthenticatorBindingProgressRepository,
    enrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    steamAuthenticatorBindingApiClient: SteamAuthenticatorBindingApiClient,
    steamProtocolLoginOrchestrator: SteamProtocolLoginOrchestrator,
    vaultRepository: VaultRepository,
    steamSessionRepository: SteamSessionRepository,
    vaultCryptography: VaultCryptography,
    onBindingCompleted: () -> Unit,
    onOpenCompatibilityImport: (ImportTokenEntryContext) -> Unit,
    onReturnToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storedBindingContext by remember { mutableStateOf<SteamAuthenticatorBindingContext?>(null) }
    var storedDraft by remember { mutableStateOf<SteamAuthenticatorEnrollmentDraft?>(null) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var isBeginning by rememberSaveable { mutableStateOf(false) }
    var beginError by rememberSaveable { mutableStateOf<String?>(null) }
    var beginFailure by remember { mutableStateOf<Throwable?>(null) }
    var beginResult by remember { mutableStateOf<SteamAuthenticatorBeginResult?>(null) }
    var bindingProgress by remember { mutableStateOf<SteamAuthenticatorBindingProgressDraft?>(null) }
    var serverTimeOffsetSeconds by rememberSaveable { mutableStateOf(0L) }
    var progressStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var activationCode by rememberSaveable { mutableStateOf("") }
    var webApiKeyInput by rememberSaveable { mutableStateOf("") }
    var selectedAuthModeName by rememberSaveable { mutableStateOf<String?>(null) }
    var isFinalizing by rememberSaveable { mutableStateOf(false) }
    var finalizeError by rememberSaveable { mutableStateOf<String?>(null) }
    var finalizeFailure by remember { mutableStateOf<Throwable?>(null) }
    var finalizeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneRecoveryStage by remember { mutableStateOf<SteamAuthenticatorPhoneRecoveryStage?>(null) }
    var isProcessingPhoneRecovery by rememberSaveable { mutableStateOf(false) }
    var phoneRecoveryStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneRecoveryErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneNumberInput by rememberSaveable { mutableStateOf("") }
    var phoneCountryCodeInput by rememberSaveable { mutableStateOf("") }
    var phoneSmsCodeInput by rememberSaveable { mutableStateOf("") }
    var phoneConfirmationEmailAddress by rememberSaveable { mutableStateOf("") }
    var phoneNumberFormatted by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(bindingContextRepository, enrollmentDraftRepository) {
        loadError = null
        storedBindingContext = try {
            bindingContextRepository.getContext()
        } catch (error: Exception) {
            loadError = error.message
            null
        }
        storedDraft = try {
            enrollmentDraftRepository.getDraft()
        } catch (error: Exception) {
            loadError = error.message
            null
        }
    }

    LaunchedEffect(
        storedBindingContext?.session?.sessionId,
        storedBindingContext?.capturedAt,
        storedBindingContext?.webApiKey,
        storedDraft?.sessionId,
        storedDraft?.capturedAt,
        storedDraft?.webApiKey,
    ) {
        webApiKeyInput = storedBindingContext?.webApiKey ?: storedDraft?.webApiKey.orEmpty()
    }

    val effectiveBindingContext = remember(storedBindingContext, webApiKeyInput) {
        storedBindingContext?.copy(
            webApiKey = webApiKeyInput.trim().takeIf { it.isNotBlank() },
        )
    }
    val effectiveDraft = remember(storedDraft, webApiKeyInput) {
        storedDraft?.copy(
            webApiKey = webApiKeyInput.trim().takeIf { it.isNotBlank() },
        )
    }
    val bindingContextPreparationResult = remember(effectiveBindingContext) {
        effectiveBindingContext?.let { bindingContext ->
            runCatching { SteamAuthenticatorBindingPreparationFactory.from(bindingContext) }
        }
    }
    val draftPreparationResult = remember(effectiveDraft) {
        effectiveDraft?.let { draft ->
            runCatching { SteamAuthenticatorBindingPreparationFactory.from(draft) }
        }
    }
    val preparationResult = remember(bindingContextPreparationResult, draftPreparationResult) {
        when {
            bindingContextPreparationResult?.isSuccess == true -> bindingContextPreparationResult
            draftPreparationResult != null -> draftPreparationResult
            else -> bindingContextPreparationResult
        }
    }
    val preparation = preparationResult?.getOrNull()
    val preparationError = preparationResult?.exceptionOrNull()?.message
    val availableAuthModes = remember(preparation) {
        preparation?.let(SteamAuthenticatorBindingAuthModeFactory::availableModes).orEmpty()
    }
    val preferredAuthMode = remember(preparation) {
        preparation?.let(SteamAuthenticatorBindingAuthModeFactory::preferredMode)
    }
    LaunchedEffect(availableAuthModes, preferredAuthMode) {
        val currentMode = selectedAuthModeName
            ?.let { name -> SteamAuthenticatorBindingAuthMode.entries.firstOrNull { it.name == name } }
        if (currentMode == null || currentMode !in availableAuthModes) {
            selectedAuthModeName = preferredAuthMode?.name
        }
    }
    val selectedAuthMode = remember(selectedAuthModeName, availableAuthModes) {
        selectedAuthModeName
            ?.let { name -> SteamAuthenticatorBindingAuthMode.entries.firstOrNull { it.name == name } }
            ?.takeIf { it in availableAuthModes }
            ?: availableAuthModes.firstOrNull()
    }
    val beginGuidance = remember(beginFailure, beginError, selectedAuthMode, availableAuthModes) {
        SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = beginFailure,
            errorMessage = beginError,
            phase = SteamAuthenticatorBindingFailurePhase.BEGIN,
            currentMode = selectedAuthMode,
            availableModes = availableAuthModes,
        )
    }
    val finalizeGuidance = remember(finalizeFailure, finalizeError, selectedAuthMode, availableAuthModes) {
        SteamAuthenticatorBindingFailureGuidanceFactory.from(
            error = finalizeFailure,
            errorMessage = finalizeError,
            phase = SteamAuthenticatorBindingFailurePhase.FINALIZE,
            currentMode = selectedAuthMode,
            availableModes = availableAuthModes,
        )
    }
    val beginFailureReason = remember(beginFailure) {
        (beginFailure as? SteamAuthenticatorBindingException)?.reason
    }
    val showPhoneRecoveryCard = remember(beginFailureReason, phoneRecoveryStage) {
        phoneRecoveryStage != null ||
            beginFailureReason == SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED
    }

    LaunchedEffect(bindingProgressRepository, preparation?.sourceSignature) {
        val draftSignature = preparation?.sourceSignature ?: return@LaunchedEffect
        val savedProgress = runCatching { bindingProgressRepository.getProgress() }.getOrNull()
        if (savedProgress?.enrollmentDraftSignature == draftSignature) {
            bindingProgress = savedProgress
            beginResult = savedProgress.beginResult
            serverTimeOffsetSeconds = savedProgress.serverTimeOffsetSeconds
            val restoredMessage = savedProgress.statusMessage ?: context.getString(
                R.string.steam_authenticator_binding_progress_restored,
            )
            progressStatusMessage = restoredMessage
            finalizeMessage = restoredMessage
        } else if (savedProgress != null) {
            runCatching { bindingProgressRepository.clearProgress() }
            bindingProgress = null
            beginResult = null
            serverTimeOffsetSeconds = 0L
            progressStatusMessage = null
            finalizeMessage = null
        } else {
            bindingProgress = null
            beginResult = null
            serverTimeOffsetSeconds = 0L
            progressStatusMessage = null
            finalizeMessage = null
        }
    }

    suspend fun saveBoundToken(
        result: SteamAuthenticatorBeginResult,
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        val importDraft = SteamAuthenticatorBindingImportDraftFactory.from(
            preparation = bindingPreparation,
            beginResult = result,
        )
        val token = vaultRepository.saveImportedToken(importDraft)
        val protocolContext = bindingPreparation.bindingContext
        if (protocolContext != null) {
            steamProtocolLoginOrchestrator.saveSessionForToken(
                tokenId = token.id,
                accountName = token.accountName,
                session = protocolContext.session,
            )
        } else {
            val now = Instant.now().toString()
            steamSessionRepository.saveSession(
                SteamSessionRecord(
                    tokenId = token.id,
                    accountName = token.accountName,
                    steamId = bindingPreparation.resolvedSteamId ?: result.steamId,
                    sessionId = bindingPreparation.sessionId,
                    cookies = bindingPreparation.cookies,
                    oauthToken = bindingPreparation.oauthToken,
                    createdAt = now,
                    updatedAt = now,
                    lastValidatedAt = null,
                    validationStatus = SteamSessionValidationStatus.UNKNOWN,
                    lastValidationErrorMessage = null,
                ),
            )
        }
        bindingProgressRepository.clearProgress()
        bindingProgress = null
        bindingContextRepository.clearContext()
        storedBindingContext = null
        enrollmentDraftRepository.clearDraft()
        storedDraft = null
        onBindingCompleted()
    }

    suspend fun persistPreparationIfNeeded(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ): SteamAuthenticatorBindingPreparation {
        val trimmedWebApiKey = webApiKeyInput.trim().takeIf { it.isNotBlank() }
        val currentDraft = bindingPreparation.draft
        if (currentDraft != null) {
            if (currentDraft.webApiKey == trimmedWebApiKey) {
                return bindingPreparation
            }
            val updatedDraft = currentDraft.copy(webApiKey = trimmedWebApiKey)
            enrollmentDraftRepository.saveDraft(updatedDraft)
            storedDraft = updatedDraft
            return SteamAuthenticatorBindingPreparationFactory.from(updatedDraft)
        }
        val currentBindingContext = bindingPreparation.bindingContext
            ?: return bindingPreparation
        if (currentBindingContext.webApiKey == trimmedWebApiKey) {
            return bindingPreparation
        }
        val updatedBindingContext = currentBindingContext.copy(webApiKey = trimmedWebApiKey)
        bindingContextRepository.saveContext(updatedBindingContext)
        storedBindingContext = updatedBindingContext
        return SteamAuthenticatorBindingPreparationFactory.from(updatedBindingContext)
    }

    suspend fun saveBindingProgress(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
        result: SteamAuthenticatorBeginResult,
        offsetSeconds: Long,
        stage: SteamAuthenticatorBindingProgressStage,
        statusMessage: String?,
    ) {
        val now = Instant.now().toString()
        val draft = SteamAuthenticatorBindingProgressDraft(
            enrollmentDraftSignature = bindingPreparation.sourceSignature,
            begunAt = bindingProgress?.begunAt ?: now,
            beginResult = result,
            serverTimeOffsetSeconds = offsetSeconds,
            stage = stage,
            lastUpdatedAt = now,
            statusMessage = statusMessage,
        )
        bindingProgressRepository.saveProgress(draft)
        bindingProgress = draft
    }

    fun buildPhoneAccessRequest(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ): SteamAuthenticatorPhoneAccessRequest? {
        val oauthToken = bindingPreparation.oauthToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return SteamAuthenticatorPhoneAccessRequest(
            oauthToken = oauthToken,
            steamId = bindingPreparation.resolvedSteamId,
        )
    }

    fun buildExistingAuthenticatorImportContext(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ): ImportTokenEntryContext {
        return ImportTokenEntryContext.existingAuthenticator(
            accountName = bindingPreparation.accountName,
            steamId = bindingPreparation.resolvedSteamId,
            deviceId = bindingPreparation.generatedDeviceId,
        )
    }

    fun markPhoneRecoveryReadyForRetry() {
        phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.READY_TO_RETRY_BINDING
        phoneRecoveryErrorMessage = null
        phoneRecoveryStatusMessage = context.getString(
            R.string.steam_authenticator_binding_phone_verified,
        )
        beginError = null
        beginFailure = null
    }

    fun phoneEmailSentMessage(emailAddress: String?): String {
        return emailAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { address ->
                context.getString(
                    R.string.steam_authenticator_binding_phone_email_sent,
                    address,
                )
            }
            ?: context.getString(
                R.string.steam_authenticator_binding_phone_email_sent_generic,
            )
    }

    fun phoneSmsSentMessage(): String {
        return phoneNumberFormatted
            .takeIf { it.isNotBlank() }
            ?.let { formattedNumber ->
                context.getString(
                    R.string.steam_authenticator_binding_phone_sms_sent,
                    formattedNumber,
                )
            }
            ?: context.getString(
                R.string.steam_authenticator_binding_phone_sms_sent_generic,
            )
    }

    fun buildPhoneAccessRequestOrReportError(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ): SteamAuthenticatorPhoneAccessRequest? {
        return buildPhoneAccessRequest(bindingPreparation) ?: run {
            phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.ENTER_PHONE
            phoneRecoveryErrorMessage = context.getString(
                R.string.steam_authenticator_binding_phone_oauth_required,
            )
            null
        }
    }

    suspend fun beginBindingRequest(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        isBeginning = true
        beginError = null
        beginFailure = null
        finalizeError = null
        finalizeFailure = null
        finalizeMessage = null
        phoneRecoveryErrorMessage = null
        try {
            val activePreparation = persistPreparationIfNeeded(
                bindingPreparation,
            )
            val activeAuth = SteamAuthenticatorBindingAuthModeFactory.resolve(
                mode = selectedAuthMode
                    ?: SteamAuthenticatorBindingAuthModeFactory.preferredMode(
                        activePreparation,
                    )
                    ?: SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                preparation = activePreparation,
            )
            val result = steamAuthenticatorBindingApiClient.beginAuthenticatorBinding(
                SteamAuthenticatorBeginRequest(
                    steamId = activePreparation.resolvedSteamId!!,
                    oauthToken = activeAuth.oauthToken,
                    webApiKey = activeAuth.webApiKey,
                    deviceId = activePreparation.generatedDeviceId!!,
                    sessionId = activePreparation.sessionId,
                    cookies = activePreparation.cookies,
                ),
            )
            beginResult = result
            val restoredOffset = result.serverTimeSeconds?.let { serverTime ->
                serverTime - (System.currentTimeMillis() / 1000L)
            } ?: 0L
            serverTimeOffsetSeconds = restoredOffset
            val statusMessage = context.getString(
                R.string.steam_authenticator_binding_progress_saved,
            )
            saveBindingProgress(
                bindingPreparation = activePreparation,
                result = result,
                offsetSeconds = restoredOffset,
                stage = SteamAuthenticatorBindingProgressStage.MATERIAL_READY,
                statusMessage = statusMessage,
            )
            progressStatusMessage = statusMessage
            finalizeMessage = context.getString(
                R.string.steam_authenticator_binding_begin_success,
            )
            phoneRecoveryStage = null
            phoneRecoveryStatusMessage = null
            phoneRecoveryErrorMessage = null
            phoneSmsCodeInput = ""
            phoneConfirmationEmailAddress = ""
            phoneNumberFormatted = ""
        } catch (error: Exception) {
            beginFailure = error
            beginError = error.message
                ?: context.getString(
                    R.string.steam_authenticator_binding_begin_failed_generic,
                )
            val failureReason = (error as? SteamAuthenticatorBindingException)?.reason
            if (failureReason == SteamAuthenticatorBindingFailureReason.PHONE_NUMBER_REQUIRED) {
                if (phoneRecoveryStage == null) {
                    phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.ENTER_PHONE
                }
                phoneRecoveryStatusMessage = context.getString(
                    R.string.steam_authenticator_binding_phone_required_intro,
                )
                phoneRecoveryErrorMessage = null
            } else {
                phoneRecoveryStage = null
                phoneRecoveryStatusMessage = null
                phoneRecoveryErrorMessage = null
            }
        } finally {
            isBeginning = false
        }
    }

    suspend fun submitPhoneNumber(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        val accessRequest = buildPhoneAccessRequestOrReportError(bindingPreparation) ?: return
        val trimmedPhoneNumber = phoneNumberInput.trim()
        if (trimmedPhoneNumber.isEmpty()) {
            phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.ENTER_PHONE
            phoneRecoveryErrorMessage = context.getString(
                R.string.steam_authenticator_binding_phone_number_required,
            )
            return
        }
        isProcessingPhoneRecovery = true
        phoneRecoveryErrorMessage = null
        try {
            val phoneStatus = steamAuthenticatorBindingApiClient.getPhoneStatus(accessRequest)
            if (phoneStatus.verifiedPhone) {
                markPhoneRecoveryReadyForRetry()
                return
            }
            val resolvedCountryCode = phoneCountryCodeInput
                .trim()
                .takeIf { it.isNotBlank() }
                ?: steamAuthenticatorBindingApiClient.getUserCountry(accessRequest)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    context.getString(
                        R.string.steam_authenticator_binding_phone_country_failed,
                    ),
                )
            phoneCountryCodeInput = resolvedCountryCode
            val setResult = steamAuthenticatorBindingApiClient.setPhoneNumber(
                SteamAuthenticatorSetPhoneNumberRequest(
                    oauthToken = accessRequest.oauthToken,
                    phoneNumber = trimmedPhoneNumber,
                    countryCode = resolvedCountryCode,
                ),
            )
            phoneConfirmationEmailAddress = setResult.confirmationEmailAddress.orEmpty()
            phoneNumberFormatted = setResult.phoneNumberFormatted
                ?.takeIf { it.isNotBlank() }
                ?: trimmedPhoneNumber
            phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.WAITING_EMAIL_CONFIRMATION
            phoneRecoveryStatusMessage = phoneEmailSentMessage(phoneConfirmationEmailAddress)
            phoneSmsCodeInput = ""
        } catch (error: Exception) {
            phoneRecoveryErrorMessage = error.message
                ?: context.getString(
                    R.string.steam_authenticator_binding_phone_set_failed,
                )
        } finally {
            isProcessingPhoneRecovery = false
        }
    }

    suspend fun sendPhoneVerificationCode(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        val accessRequest = buildPhoneAccessRequestOrReportError(bindingPreparation) ?: return
        isProcessingPhoneRecovery = true
        phoneRecoveryErrorMessage = null
        try {
            steamAuthenticatorBindingApiClient.sendPhoneVerificationCode(accessRequest)
            phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.WAITING_SMS_CODE
            phoneRecoveryStatusMessage = phoneSmsSentMessage()
        } catch (error: Exception) {
            phoneRecoveryErrorMessage = error.message
                ?: context.getString(
                    R.string.steam_authenticator_binding_phone_send_sms_failed,
                )
        } finally {
            isProcessingPhoneRecovery = false
        }
    }

    suspend fun confirmPhoneEmail(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        val accessRequest = buildPhoneAccessRequestOrReportError(bindingPreparation) ?: return
        isProcessingPhoneRecovery = true
        phoneRecoveryErrorMessage = null
        try {
            val phoneStatus = steamAuthenticatorBindingApiClient.getPhoneStatus(accessRequest)
            if (phoneStatus.verifiedPhone) {
                markPhoneRecoveryReadyForRetry()
                return
            }
            val emailStatus = steamAuthenticatorBindingApiClient.getEmailConfirmationStatus(
                accessRequest,
            )
            if (emailStatus.awaitingEmailConfirmation) {
                phoneRecoveryStage =
                    SteamAuthenticatorPhoneRecoveryStage.WAITING_EMAIL_CONFIRMATION
                phoneRecoveryStatusMessage = emailStatus.secondsToWait
                    ?.takeIf { it > 0 }
                    ?.let { secondsToWait ->
                        context.getString(
                            R.string.steam_authenticator_binding_phone_email_still_pending_with_wait,
                            secondsToWait,
                        )
                    }
                    ?: context.getString(
                        R.string.steam_authenticator_binding_phone_email_still_pending,
                    )
                return
            }
            sendPhoneVerificationCode(bindingPreparation)
        } catch (error: Exception) {
            phoneRecoveryErrorMessage = error.message
                ?: context.getString(
                    R.string.steam_authenticator_binding_phone_email_status_failed,
                )
        } finally {
            isProcessingPhoneRecovery = false
        }
    }

    suspend fun verifyPhoneCode(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
    ) {
        val accessRequest = buildPhoneAccessRequestOrReportError(bindingPreparation) ?: return
        val trimmedCode = phoneSmsCodeInput.trim()
        if (trimmedCode.isEmpty()) {
            phoneRecoveryStage = SteamAuthenticatorPhoneRecoveryStage.WAITING_SMS_CODE
            phoneRecoveryErrorMessage = context.getString(
                R.string.steam_authenticator_binding_phone_sms_code_required,
            )
            return
        }
        isProcessingPhoneRecovery = true
        phoneRecoveryErrorMessage = null
        try {
            steamAuthenticatorBindingApiClient.verifyPhoneWithCode(
                SteamAuthenticatorVerifyPhoneCodeRequest(
                    oauthToken = accessRequest.oauthToken,
                    code = trimmedCode,
                ),
            )
            phoneSmsCodeInput = ""
            markPhoneRecoveryReadyForRetry()
        } catch (error: Exception) {
            phoneRecoveryErrorMessage = error.message
                ?: context.getString(
                    R.string.steam_authenticator_binding_phone_verify_failed,
                )
        } finally {
            isProcessingPhoneRecovery = false
        }
    }

    suspend fun detectServerSideBindingCompletion(
        bindingPreparation: SteamAuthenticatorBindingPreparation,
        authMode: SteamAuthenticatorBindingResolvedAuth,
    ): Boolean {
        return try {
            steamAuthenticatorBindingApiClient.beginAuthenticatorBinding(
                SteamAuthenticatorBeginRequest(
                    steamId = bindingPreparation.resolvedSteamId!!,
                    oauthToken = authMode.oauthToken,
                    webApiKey = authMode.webApiKey,
                    deviceId = bindingPreparation.generatedDeviceId!!,
                    sessionId = bindingPreparation.sessionId,
                    cookies = bindingPreparation.cookies,
                ),
            )
            false
        } catch (error: SteamAuthenticatorBindingException) {
            error.reason == SteamAuthenticatorBindingFailureReason.AUTHENTICATOR_ALREADY_PRESENT
        } catch (_: Exception) {
            false
        }
    }

    val progressSteps = listOf(
        VaultStepItem(
            title = context.getString(R.string.steam_authenticator_binding_session_title),
            subtitle = context.getString(R.string.steam_authenticator_binding_session_description),
            state = when {
                preparation != null -> VaultStepState.Complete
                storedBindingContext != null || storedDraft != null -> VaultStepState.Active
                else -> VaultStepState.Pending
            },
        ),
        VaultStepItem(
            title = context.getString(R.string.steam_authenticator_binding_begin_title),
            subtitle = context.getString(R.string.steam_authenticator_binding_begin_description),
            state = when {
                beginResult != null -> VaultStepState.Complete
                preparation?.isReadyForBinding == true -> VaultStepState.Active
                else -> VaultStepState.Pending
            },
        ),
        VaultStepItem(
            title = context.getString(R.string.steam_authenticator_binding_activation_title),
            subtitle = context.getString(R.string.steam_authenticator_binding_activation_description),
            state = when {
                beginResult?.fullyEnrolled == true -> VaultStepState.Complete
                beginResult != null || showPhoneRecoveryCard -> VaultStepState.Active
                else -> VaultStepState.Pending
            },
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.steam_authenticator_binding_title),
                subtitle = stringResource(R.string.steam_authenticator_binding_overview_description),
            )
        }
        item {
            VaultProgressSteps(steps = progressSteps)
        }
        item {
            VaultInlineBanner(
                text = stringResource(R.string.steam_authenticator_binding_overview_note),
                tone = VaultBannerTone.Neutral,
            )
        }
        loadError?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Warning,
                )
            }
        }
        if (storedBindingContext == null && storedDraft == null) {
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_authenticator_binding_missing_draft_title),
                    description = loadError
                        ?: stringResource(
                            R.string.steam_authenticator_binding_missing_draft_description,
                        ),
                ) {
                    VaultPrimaryButton(
                        text = stringResource(R.string.steam_authenticator_binding_return_action),
                        onClick = onReturnToSignIn,
                    )
                }
            }
        } else {
            if (preparation == null && preparationError != null) {
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_authenticator_binding_parse_failed_title),
                        description = preparationError
                            ?: stringResource(
                            R.string.steam_authenticator_binding_parse_failed_description,
                        ),
                    ) {
                        VaultPrimaryButton(
                            text = stringResource(R.string.steam_authenticator_binding_return_action),
                            onClick = onReturnToSignIn,
                        )
                    }
                }
            }
            preparation?.let { bindingPreparation ->
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_authenticator_binding_session_title),
                        description = stringResource(
                            R.string.steam_authenticator_binding_session_description,
                        ),
                    ) {
                        bindingPreparation.resolvedSteamId?.let { steamId ->
                            VaultKeyValueRow(
                                label = stringResource(R.string.steam_session_detail_steam_id),
                                value = steamId,
                            )
                        }
                        bindingPreparation.accountName?.let { accountName ->
                            VaultKeyValueRow(
                                label = stringResource(R.string.import_label_manual_account_name),
                                value = accountName,
                            )
                        }
                        VaultSensitiveValueRow(
                            label = stringResource(R.string.steam_session_editor_session_id_label),
                            value = bindingPreparation.sessionId,
                            copyDescription = stringResource(
                                R.string.steam_session_editor_session_id_label,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.steam_authenticator_binding_captured_at,
                                bindingPreparation.capturedAt,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (bindingPreparation.currentUrl != null) {
                            VaultKeyValueRow(
                                label = stringResource(
                                    R.string.steam_add_authenticator_manual_current_url_label,
                                ),
                                value = bindingPreparation.currentUrl,
                            )
                        } else {
                            VaultInlineBanner(
                                text = stringResource(
                                    R.string.steam_authenticator_binding_source_kind_protocol_login,
                                ),
                                tone = VaultBannerTone.Neutral,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_authenticator_binding_checks_title),
                        description = stringResource(
                            R.string.steam_authenticator_binding_checks_description,
                        ),
                    ) {
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_authenticator_binding_check_session_id,
                            ),
                            highlighted = bindingPreparation.sessionIdCookie != null,
                        )
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_authenticator_binding_check_steam_login_secure,
                            ),
                            highlighted = bindingPreparation.steamLoginSecureCookie != null,
                        )
                        ChecklistRow(
                            label = stringResource(
                                if (bindingPreparation.steamLoginCookie != null) {
                                    R.string.steam_authenticator_binding_check_steam_login_present
                                } else {
                                    R.string.steam_authenticator_binding_check_steam_login_missing
                                },
                            ),
                            highlighted = bindingPreparation.steamLoginCookie != null,
                        )
                        ChecklistRow(
                            label = stringResource(
                                if (bindingPreparation.resolvedSteamId != null) {
                                    R.string.steam_authenticator_binding_check_steam_id_ready
                                } else {
                                    R.string.steam_authenticator_binding_check_steam_id_missing
                                },
                            ),
                            highlighted = bindingPreparation.resolvedSteamId != null,
                        )

                        ChecklistRow(
                            label = stringResource(
                                if (bindingPreparation.isReadyForBinding) {
                                    R.string.steam_authenticator_binding_check_ready
                                } else {
                                    R.string.steam_authenticator_binding_check_not_ready
                                },
                            ),
                            highlighted = bindingPreparation.isReadyForBinding,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_authenticator_binding_cookie_title),
                        description = stringResource(
                            R.string.steam_authenticator_binding_cookie_description,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.steam_authenticator_binding_cookie_count,
                                bindingPreparation.cookies.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.steam_authenticator_binding_cookie_names,
                                bindingPreparation.cookieNames.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        bindingPreparation.generatedDeviceId?.let { deviceId ->
                            VaultSensitiveValueRow(
                                label = stringResource(R.string.token_detail_metadata_device_id),
                                value = deviceId,
                                copyDescription = stringResource(R.string.token_detail_metadata_device_id),
                            )
                        }
                        if (bindingPreparation.supportsEditableWebApiKey) {
                            VaultTextField(
                                value = webApiKeyInput,
                                onValueChange = { webApiKeyInput = it },
                                label = stringResource(
                                    R.string.steam_authenticator_binding_web_api_key_label,
                                ),
                                supportingText = stringResource(
                                    R.string.steam_authenticator_binding_web_api_key_supporting,
                                ),
                                singleLine = true,
                            )
                        }
                    }
                }
                if (availableAuthModes.isNotEmpty()) {
                    item {
                        ScreenSectionCard(
                            title = stringResource(
                                R.string.steam_authenticator_binding_auth_mode_title,
                            ),
                            description = stringResource(
                                R.string.steam_authenticator_binding_auth_mode_description,
                            ),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableAuthModes.forEach { authMode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAuthModeName = authMode.name },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        RadioButton(
                                            selected = selectedAuthMode == authMode,
                                            onClick = { selectedAuthModeName = authMode.name },
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = authModeLabel(authMode),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = authModeSupportingText(authMode),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showPhoneRecoveryCard && beginResult == null) {
                    item {
                        val recoveryStage = phoneRecoveryStage
                            ?: SteamAuthenticatorPhoneRecoveryStage.ENTER_PHONE
                        val hasOauthToken = bindingPreparation.oauthToken
                            ?.trim()
                            ?.isNotEmpty() == true
                        ScreenSectionCard(
                            title = stringResource(R.string.steam_authenticator_binding_phone_title),
                            description = stringResource(
                                R.string.steam_authenticator_binding_phone_description,
                            ),
                        ) {
                            phoneRecoveryStatusMessage?.let { message ->
                                VaultInlineBanner(
                                    text = message,
                                    tone = VaultBannerTone.Success,
                                )
                            }
                            phoneRecoveryErrorMessage?.let { message ->
                                VaultInlineBanner(
                                    text = message,
                                    tone = VaultBannerTone.Error,
                                )
                            }
                            if (!hasOauthToken) {
                                VaultInlineBanner(
                                    text = stringResource(
                                        R.string.steam_authenticator_binding_phone_oauth_required,
                                    ),
                                    tone = VaultBannerTone.Warning,
                                )
                            }
                            when (recoveryStage) {
                                SteamAuthenticatorPhoneRecoveryStage.ENTER_PHONE -> {
                                    VaultTextField(
                                        value = phoneNumberInput,
                                        onValueChange = { phoneNumberInput = it },
                                        label = stringResource(
                                            R.string.steam_authenticator_binding_phone_number_label,
                                        ),
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                        singleLine = true,
                                    )
                                    VaultTextField(
                                        value = phoneCountryCodeInput,
                                        onValueChange = { phoneCountryCodeInput = it },
                                        label = stringResource(
                                            R.string.steam_authenticator_binding_phone_country_code_label,
                                        ),
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                        singleLine = true,
                                    )
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            if (isProcessingPhoneRecovery) {
                                                R.string.steam_authenticator_binding_begin_loading
                                            } else {
                                                R.string.steam_authenticator_binding_phone_submit_action
                                            },
                                        ),
                                        onClick = {
                                            scope.launch {
                                                submitPhoneNumber(bindingPreparation)
                                            }
                                        },
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                    )
                                }

                                SteamAuthenticatorPhoneRecoveryStage.WAITING_EMAIL_CONFIRMATION -> {
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            if (isProcessingPhoneRecovery) {
                                                R.string.steam_authenticator_binding_begin_loading
                                            } else {
                                                R.string.steam_authenticator_binding_phone_email_check_action
                                            },
                                        ),
                                        onClick = {
                                            scope.launch {
                                                confirmPhoneEmail(bindingPreparation)
                                            }
                                        },
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                    )
                                }

                                SteamAuthenticatorPhoneRecoveryStage.WAITING_SMS_CODE -> {
                                    VaultTextField(
                                        value = phoneSmsCodeInput,
                                        onValueChange = { phoneSmsCodeInput = it },
                                        label = stringResource(
                                            R.string.steam_authenticator_binding_phone_sms_code_label,
                                        ),
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                        singleLine = true,
                                    )
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            if (isProcessingPhoneRecovery) {
                                                R.string.steam_authenticator_binding_begin_loading
                                            } else {
                                                R.string.steam_authenticator_binding_phone_verify_action
                                            },
                                        ),
                                        onClick = {
                                            scope.launch {
                                                verifyPhoneCode(bindingPreparation)
                                            }
                                        },
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                    )
                                    VaultSecondaryButton(
                                        text = stringResource(
                                            R.string.steam_authenticator_binding_phone_resend_sms_action,
                                        ),
                                        onClick = {
                                            scope.launch {
                                                sendPhoneVerificationCode(bindingPreparation)
                                            }
                                        },
                                        enabled = !isProcessingPhoneRecovery && hasOauthToken,
                                    )
                                }

                                SteamAuthenticatorPhoneRecoveryStage.READY_TO_RETRY_BINDING -> {
                                    VaultPrimaryButton(
                                        text = stringResource(
                                            if (isBeginning) {
                                                R.string.steam_authenticator_binding_begin_loading
                                            } else {
                                                R.string.steam_authenticator_binding_phone_retry_begin_action
                                            },
                                        ),
                                        onClick = {
                                            scope.launch {
                                                beginBindingRequest(bindingPreparation)
                                            }
                                        },
                                        enabled = !isBeginning,
                                    )
                                }
                            }
                        }
                    }
                }
                if (bindingPreparation.isReadyForBinding && beginResult == null) {
                    item {
                        ScreenSectionCard(
                            title = stringResource(R.string.steam_authenticator_binding_begin_title),
                            description = stringResource(
                                R.string.steam_authenticator_binding_begin_description,
                            ),
                        ) {
                            progressStatusMessage?.let { message ->
                                VaultInlineBanner(
                                    text = message,
                                    tone = VaultBannerTone.Success,
                                )
                            }
                            selectedAuthMode?.let { authMode ->
                                VaultKeyValueRow(
                                    label = stringResource(
                                        R.string.steam_authenticator_binding_auth_mode_title,
                                    ),
                                    value = authModeLabel(authMode),
                                )
                            }
                            beginError?.let { message ->
                                VaultInlineBanner(
                                    text = message,
                                    tone = VaultBannerTone.Error,
                                )
                            }
                            beginGuidance?.let { guidance ->
                                VaultInlineBanner(
                                    text = bindingFailureGuidanceMessage(guidance),
                                    tone = VaultBannerTone.Warning,
                                )
                                guidance.suggestedMode?.let { suggestedMode ->
                                    VaultSecondaryButton(
                                        text = stringResource(
                                            R.string.steam_authenticator_binding_guidance_switch_auth_mode_action,
                                            authModeLabel(suggestedMode),
                                        ),
                                        onClick = { selectedAuthModeName = suggestedMode.name },
                                    )
                                }
                                if (guidance.kind == SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN) {
                                    VaultSecondaryButton(
                                        text = stringResource(
                                            R.string.steam_authenticator_binding_guidance_return_to_sign_in_action,
                                        ),
                                        onClick = onReturnToSignIn,
                                    )
                                }
                                if (guidance.kind == SteamAuthenticatorBindingFailureGuidanceKind.OPEN_COMPATIBILITY_IMPORT) {
                                    VaultSecondaryButton(
                                        text = stringResource(
                                            R.string.steam_authenticator_binding_guidance_open_import_action,
                                        ),
                                        onClick = {
                                            onOpenCompatibilityImport(
                                                buildExistingAuthenticatorImportContext(
                                                    bindingPreparation,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                            VaultPrimaryButton(
                                text = stringResource(
                                    if (isBeginning) {
                                        R.string.steam_authenticator_binding_begin_loading
                                    } else {
                                        R.string.steam_authenticator_binding_begin_action
                                    },
                                ),
                                onClick = {
                                    scope.launch {
                                        beginBindingRequest(bindingPreparation)
                                    }
                                },
                                enabled = !isBeginning,
                            )
                        }
                    }
                }
                beginResult?.let { result ->
                    item {
                        ScreenSectionCard(
                            title = stringResource(
                                R.string.steam_authenticator_binding_materials_title,
                            ),
                            description = stringResource(
                                R.string.steam_authenticator_binding_materials_description,
                            ),
                        ) {
                            finalizeMessage?.let { message ->
                                VaultInlineBanner(
                                    text = message,
                                    tone = VaultBannerTone.Success,
                                )
                            }
                            selectedAuthMode?.let { authMode ->
                                VaultKeyValueRow(
                                    label = stringResource(
                                        R.string.steam_authenticator_binding_auth_mode_title,
                                    ),
                                    value = authModeLabel(authMode),
                                )
                            }
                            bindingProgress?.let { progress ->
                                VaultInlineBanner(
                                    text = stringResource(
                                        R.string.steam_authenticator_binding_materials_stage,
                                        stringResource(
                                            when (progress.stage) {
                                                SteamAuthenticatorBindingProgressStage.MATERIAL_READY -> {
                                                    R.string.steam_authenticator_binding_stage_material_ready
                                                }

                                                SteamAuthenticatorBindingProgressStage.WAITING_NEXT_ACTIVATION_CODE -> {
                                                    R.string.steam_authenticator_binding_stage_waiting_next_activation_code
                                                }
                                            },
                                        ),
                                    ),
                                    tone = VaultBannerTone.Neutral,
                                )
                            }
                            VaultKeyValueRow(
                                label = stringResource(R.string.import_label_manual_account_name),
                                value = result.accountName ?: stringResource(
                                    R.string.steam_authenticator_binding_material_account_name_unknown,
                                ),
                            )
                            result.tokenGid?.let { tokenGid ->
                                Text(
                                    text = stringResource(
                                        R.string.steam_authenticator_binding_material_token_gid,
                                        tokenGid,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            result.revocationCode?.let { revocationCode ->
                                Text(
                                    text = stringResource(
                                        R.string.steam_authenticator_binding_material_revocation_code,
                                        revocationCode,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ChecklistRow(
                                label = stringResource(
                                    R.string.steam_authenticator_binding_material_shared_secret_ready,
                                ),
                                highlighted = true,
                            )
                            ChecklistRow(
                                label = stringResource(
                                    if (result.identitySecret != null) {
                                        R.string.steam_authenticator_binding_material_identity_secret_ready
                                    } else {
                                        R.string.steam_authenticator_binding_material_identity_secret_missing
                                    },
                                ),
                                highlighted = result.identitySecret != null,
                            )
                        }
                    }
                    if (!result.fullyEnrolled) {
                        item {
                            ScreenSectionCard(
                                title = stringResource(
                                    R.string.steam_authenticator_binding_activation_title,
                                ),
                                description = stringResource(
                                    R.string.steam_authenticator_binding_activation_description,
                                ),
                            ) {
                                finalizeError?.let { message ->
                                    VaultInlineBanner(
                                        text = message,
                                        tone = VaultBannerTone.Error,
                                    )
                                }
                                finalizeGuidance?.let { guidance ->
                                    VaultInlineBanner(
                                        text = bindingFailureGuidanceMessage(guidance),
                                        tone = VaultBannerTone.Warning,
                                    )
                                    guidance.suggestedMode?.let { suggestedMode ->
                                        VaultSecondaryButton(
                                            text = stringResource(
                                                R.string.steam_authenticator_binding_guidance_switch_auth_mode_action,
                                                authModeLabel(suggestedMode),
                                            ),
                                            onClick = { selectedAuthModeName = suggestedMode.name },
                                        )
                                    }
                                    if (guidance.kind == SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN) {
                                        VaultSecondaryButton(
                                            text = stringResource(
                                                R.string.steam_authenticator_binding_guidance_return_to_sign_in_action,
                                            ),
                                            onClick = onReturnToSignIn,
                                        )
                                    }
                                }
                                VaultTextField(
                                    value = activationCode,
                                    onValueChange = { activationCode = it },
                                    label = stringResource(
                                        R.string.steam_authenticator_binding_activation_code_label,
                                    ),
                                    supportingText = stringResource(
                                        R.string.steam_authenticator_binding_activation_code_supporting,
                                    ),
                                    enabled = !isFinalizing,
                                    singleLine = true,
                                )
                                VaultPrimaryButton(
                                    text = stringResource(
                                        if (isFinalizing) {
                                            R.string.steam_authenticator_binding_finalize_loading
                                        } else {
                                            R.string.steam_authenticator_binding_finalize_action
                                        },
                                    ),
                                    onClick = {
                                        scope.launch {
                                            if (activationCode.trim().isEmpty()) {
                                                finalizeFailure = null
                                                finalizeError = context.getString(
                                                    R.string.steam_authenticator_binding_activation_code_required,
                                                )
                                                return@launch
                                            }
                                            isFinalizing = true
                                            finalizeError = null
                                            finalizeFailure = null
                                            finalizeMessage = null
                                            try {
                                                val activePreparation = persistPreparationIfNeeded(
                                                    bindingPreparation,
                                                )
                                                val activeAuth = SteamAuthenticatorBindingAuthModeFactory.resolve(
                                                    mode = selectedAuthMode
                                                        ?: SteamAuthenticatorBindingAuthModeFactory.preferredMode(
                                                            activePreparation,
                                                        )
                                                        ?: SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                                                    preparation = activePreparation,
                                                )
                                                var refreshedOffset = serverTimeOffsetSeconds
                                                var shouldPersistPendingProgress = true
                                                repeat(MAX_AUTO_FINALIZE_ATTEMPTS) { attemptIndex ->
                                                    val currentSteamTimeSeconds =
                                                        (System.currentTimeMillis() / 1000L) + refreshedOffset
                                                    val authenticatorCode =
                                                        SteamSecretCodec.decode(result.sharedSecret)
                                                            .useSteamGuardCode(
                                                                vaultCryptography,
                                                                currentSteamTimeSeconds,
                                                            )
                                                    val finalizeResult = steamAuthenticatorBindingApiClient
                                                        .finalizeAuthenticatorBinding(
                                                            SteamAuthenticatorFinalizeRequest(
                                                                steamId = activePreparation.resolvedSteamId
                                                                    ?: result.steamId,
                                                                oauthToken = activeAuth.oauthToken,
                                                                webApiKey = activeAuth.webApiKey,
                                                                deviceId = activePreparation.generatedDeviceId!!,
                                                                activationCode = activationCode.trim(),
                                                                authenticatorCode = authenticatorCode,
                                                                authenticatorTimeSeconds = currentSteamTimeSeconds,
                                                                sessionId = activePreparation.sessionId,
                                                                cookies = activePreparation.cookies,
                                                            ),
                                                        )
                                                    refreshedOffset = finalizeResult.serverTimeSeconds
                                                        ?.let { serverTime ->
                                                            serverTime - (System.currentTimeMillis() / 1000L)
                                                        }
                                                        ?: refreshedOffset
                                                    if (!finalizeResult.wantsMoreActivation) {
                                                        serverTimeOffsetSeconds = refreshedOffset
                                                        shouldPersistPendingProgress = false
                                                        saveBoundToken(result, activePreparation)
                                                        return@launch
                                                    }
                                                    if (attemptIndex < MAX_AUTO_FINALIZE_ATTEMPTS - 1) {
                                                        finalizeMessage = context.getString(
                                                            R.string.steam_authenticator_binding_finalize_retrying_next_code,
                                                            attemptIndex + 2,
                                                            MAX_AUTO_FINALIZE_ATTEMPTS,
                                                        )
                                                        val referenceSteamTimeSeconds =
                                                            finalizeResult.serverTimeSeconds
                                                                ?: currentSteamTimeSeconds
                                                        delay(
                                                            millisUntilNextSteamGuardCode(
                                                                referenceSteamTimeSeconds,
                                                            ),
                                                        )
                                                    }
                                                }
                                                if (shouldPersistPendingProgress) {
                                                    serverTimeOffsetSeconds = refreshedOffset
                                                    if (
                                                        detectServerSideBindingCompletion(
                                                            bindingPreparation = activePreparation,
                                                            authMode = activeAuth,
                                                        )
                                                    ) {
                                                        val completionMessage = context.getString(
                                                            R.string.steam_authenticator_binding_finalize_server_already_bound,
                                                        )
                                                        progressStatusMessage = completionMessage
                                                        finalizeMessage = completionMessage
                                                        saveBoundToken(result, activePreparation)
                                                        return@launch
                                                    }
                                                    val statusMessage = context.getString(
                                                        R.string.steam_authenticator_binding_finalize_pending_more,
                                                    )
                                                    saveBindingProgress(
                                                        bindingPreparation = activePreparation,
                                                        result = result,
                                                        offsetSeconds = refreshedOffset,
                                                        stage = SteamAuthenticatorBindingProgressStage.WAITING_NEXT_ACTIVATION_CODE,
                                                        statusMessage = statusMessage,
                                                    )
                                                    progressStatusMessage = statusMessage
                                                    finalizeMessage = statusMessage
                                                }
                                            } catch (error: Exception) {
                                                finalizeFailure = error
                                                finalizeError = error.message
                                                    ?: context.getString(
                                                        R.string.steam_authenticator_binding_finalize_failed_generic,
                                                    )
                                            } finally {
                                                isFinalizing = false
                                            }
                                        }
                                    },
                                    enabled = !isFinalizing,
                                )
                            }
                        }
                    } else {
                        item {
                            ScreenSectionCard(
                                title = stringResource(
                                    R.string.steam_authenticator_binding_finalize_ready_title,
                                ),
                                description = stringResource(
                                    R.string.steam_authenticator_binding_finalize_ready_description,
                                ),
                            ) {
                                finalizeError?.let { message ->
                                    VaultInlineBanner(
                                        text = message,
                                        tone = VaultBannerTone.Error,
                                    )
                                }
                                finalizeGuidance?.let { guidance ->
                                    VaultInlineBanner(
                                        text = bindingFailureGuidanceMessage(guidance),
                                        tone = VaultBannerTone.Warning,
                                    )
                                    guidance.suggestedMode?.let { suggestedMode ->
                                        VaultSecondaryButton(
                                            text = stringResource(
                                                R.string.steam_authenticator_binding_guidance_switch_auth_mode_action,
                                                authModeLabel(suggestedMode),
                                            ),
                                            onClick = { selectedAuthModeName = suggestedMode.name },
                                        )
                                    }
                                    if (guidance.kind == SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN) {
                                        VaultSecondaryButton(
                                            text = stringResource(
                                                R.string.steam_authenticator_binding_guidance_return_to_sign_in_action,
                                            ),
                                            onClick = onReturnToSignIn,
                                        )
                                    }
                                }
                                VaultPrimaryButton(
                                    text = stringResource(
                                        R.string.steam_authenticator_binding_save_action,
                                    ),
                                    onClick = {
                                        scope.launch {
                                            try {
                                                saveBoundToken(result, bindingPreparation)
                                            } catch (error: Exception) {
                                                finalizeFailure = error
                                                finalizeError = error.message
                                                    ?: context.getString(
                                                        R.string.steam_authenticator_binding_save_failed,
                                                    )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_authenticator_binding_next_title),
                        description = stringResource(
                            if (bindingPreparation.isReadyForBinding) {
                                R.string.steam_authenticator_binding_next_ready_description
                            } else {
                                R.string.steam_authenticator_binding_next_blocked_description
                            },
                        ),
                    ) {
                        VaultSecondaryButton(
                            text = stringResource(R.string.steam_authenticator_binding_return_action),
                            onClick = onReturnToSignIn,
                        )
                        if (bindingProgress != null || beginResult != null) {
                            VaultSecondaryButton(
                                text = stringResource(
                                    R.string.steam_authenticator_binding_clear_progress_action,
                                ),
                                onClick = {
                                    scope.launch {
                                        runCatching { bindingProgressRepository.clearProgress() }
                                        bindingProgress = null
                                        beginResult = null
                                        serverTimeOffsetSeconds = 0L
                                        activationCode = ""
                                        beginError = null
                                        beginFailure = null
                                        finalizeFailure = null
                                        progressStatusMessage = context.getString(
                                            R.string.steam_authenticator_binding_progress_cleared,
                                        )
                                        finalizeError = null
                                        finalizeMessage = null
                                        phoneRecoveryStage = null
                                        isProcessingPhoneRecovery = false
                                        phoneRecoveryStatusMessage = null
                                        phoneRecoveryErrorMessage = null
                                        phoneNumberInput = ""
                                        phoneCountryCodeInput = ""
                                        phoneSmsCodeInput = ""
                                        phoneConfirmationEmailAddress = ""
                                        phoneNumberFormatted = ""
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun authModeLabel(mode: SteamAuthenticatorBindingAuthMode): String {
    return stringResource(
        when (mode) {
            SteamAuthenticatorBindingAuthMode.OAUTH_ONLY -> {
                R.string.steam_authenticator_binding_auth_mode_oauth_only
            }

            SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY -> {
                R.string.steam_authenticator_binding_auth_mode_web_api_key_only
            }

            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY -> {
                R.string.steam_authenticator_binding_auth_mode_both
            }
        },
    )
}

@Composable
private fun authModeSupportingText(mode: SteamAuthenticatorBindingAuthMode): String {
    return stringResource(
        when (mode) {
            SteamAuthenticatorBindingAuthMode.OAUTH_ONLY -> {
                R.string.steam_authenticator_binding_auth_mode_oauth_only_supporting
            }

            SteamAuthenticatorBindingAuthMode.WEB_API_KEY_ONLY -> {
                R.string.steam_authenticator_binding_auth_mode_web_api_key_only_supporting
            }

            SteamAuthenticatorBindingAuthMode.OAUTH_AND_WEB_API_KEY -> {
                R.string.steam_authenticator_binding_auth_mode_both_supporting
            }
        },
    )
}

@Composable
private fun bindingFailureGuidanceMessage(
    guidance: SteamAuthenticatorBindingFailureGuidance,
): String {
    return when (guidance.kind) {
        SteamAuthenticatorBindingFailureGuidanceKind.SIGN_IN_AGAIN -> {
            stringResource(R.string.steam_authenticator_binding_guidance_sign_in_again)
        }

        SteamAuthenticatorBindingFailureGuidanceKind.CHECK_ACTIVATION_CODE -> {
            stringResource(R.string.steam_authenticator_binding_guidance_check_activation_code)
        }

        SteamAuthenticatorBindingFailureGuidanceKind.RETRY_LATER -> {
            stringResource(R.string.steam_authenticator_binding_guidance_retry_later)
        }

        SteamAuthenticatorBindingFailureGuidanceKind.SWITCH_AUTH_MODE -> {
            stringResource(
                R.string.steam_authenticator_binding_guidance_switch_auth_mode,
                authModeLabel(
                    guidance.suggestedMode
                        ?: SteamAuthenticatorBindingAuthMode.OAUTH_ONLY,
                ),
            )
        }

        SteamAuthenticatorBindingFailureGuidanceKind.OPEN_COMPATIBILITY_IMPORT -> {
            stringResource(R.string.steam_authenticator_binding_guidance_open_import)
        }
    }
}

private fun ByteArray.useSteamGuardCode(
    vaultCryptography: VaultCryptography,
    epochSeconds: Long,
): String {
    return try {
        vaultCryptography.generateSteamGuardCode(this, epochSeconds)
    } finally {
        fill(0)
    }
}

private fun millisUntilNextSteamGuardCode(
    steamTimeSeconds: Long,
): Long {
    val secondsUntilNextCode = 30L - (steamTimeSeconds % 30L)
    return (secondsUntilNextCode.coerceAtLeast(1L) * 1000L) + 250L
}

private enum class SteamAuthenticatorPhoneRecoveryStage {
    ENTER_PHONE,
    WAITING_EMAIL_CONFIRMATION,
    WAITING_SMS_CODE,
    READY_TO_RETRY_BINDING,
}

private const val MAX_AUTO_FINALIZE_ATTEMPTS = 10
