package com.example.steam_vault_app.feature.importtoken

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
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamAuthenticatorBindingContext
import com.example.steam_vault_app.domain.model.SteamAuthenticatorEnrollmentDraft
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorBindingContextRepository
import com.example.steam_vault_app.domain.repository.SteamAuthenticatorEnrollmentDraftRepository
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun SteamAddAuthenticatorScreen(
    bindingContextRepository: SteamAuthenticatorBindingContextRepository,
    enrollmentDraftRepository: SteamAuthenticatorEnrollmentDraftRepository,
    onOpenBindingPreparation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browserReturn by SteamExternalBrowserLoginManager.browserReturn.collectAsState()

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
