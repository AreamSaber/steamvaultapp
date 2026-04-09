package com.example.steam_vault_app.feature.steamqrlogin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamQrLoginAuthSessionInfo
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.sync.SteamQrLoginApprovalManager
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@Composable
fun SteamQrLoginScreen(
    vaultRepository: VaultRepository,
    steamSessionRepository: SteamSessionRepository,
    steamQrLoginApprovalManager: SteamQrLoginApprovalManager,
    refreshVersion: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingChallengeUrl by SteamQrLoginLinkManager.pendingChallengeUrl.collectAsState()

    var challengeUrlInput by rememberSaveable { mutableStateOf("") }
    var selectedTokenId by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isInspecting by rememberSaveable { mutableStateOf(false) }
    var isResolving by rememberSaveable { mutableStateOf(false) }
    var authSessionInfo by remember { mutableStateOf<SteamQrLoginAuthSessionInfo?>(null) }
    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
    ) { result ->
        val scannedChallengeUrl = result.contents?.trim()?.takeIf { it.isNotEmpty() } ?: return@rememberLauncherForActivityResult
        challengeUrlInput = scannedChallengeUrl
        authSessionInfo = null
        errorMessage = null
        statusMessage = context.getString(R.string.steam_qr_login_deep_link_received)
    }

    val candidatesState by produceState<AppUiState<List<SteamQrLoginCandidate>>>(
        initialValue = AppUiState.Loading,
        key1 = vaultRepository,
        key2 = steamSessionRepository,
        key3 = refreshVersion,
    ) {
        value = try {
            val candidates = vaultRepository.getTokens()
                .mapNotNull { token ->
                    val session = steamSessionRepository.getSession(token.id) ?: return@mapNotNull null
                    if (
                        token.sharedSecret.isBlank() ||
                        session.steamId.isNullOrBlank() ||
                        resolveCandidateAccessToken(session).isNullOrBlank()
                    ) {
                        return@mapNotNull null
                    }
                    SteamQrLoginCandidate(token = token, session = session)
                }
                .sortedBy { it.token.accountName.lowercase() }
            if (candidates.isEmpty()) {
                AppUiState.Empty
            } else {
                AppUiState.Success(candidates)
            }
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.steam_qr_login_accounts_load_failed))
        }
    }

    LaunchedEffect(pendingChallengeUrl) {
        val challengeUrl = pendingChallengeUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@LaunchedEffect
        challengeUrlInput = challengeUrl
        authSessionInfo = null
        errorMessage = null
        statusMessage = context.getString(R.string.steam_qr_login_deep_link_received)
        SteamQrLoginLinkManager.clearPendingChallengeUrl()
    }

    LaunchedEffect(candidatesState) {
        val candidates = (candidatesState as? AppUiState.Success)?.value ?: return@LaunchedEffect
        if (selectedTokenId !in candidates.map { it.token.id }) {
            selectedTokenId = candidates.firstOrNull()?.token?.id
        }
    }

    val selectedCandidate = remember(candidatesState, selectedTokenId) {
        (candidatesState as? AppUiState.Success)
            ?.value
            ?.firstOrNull { it.token.id == selectedTokenId }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.steam_qr_login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_qr_login_overview_title),
                description = stringResource(R.string.steam_qr_login_overview_description),
            ) {
                ChecklistRow(
                    label = stringResource(R.string.steam_qr_login_overview_scan),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.steam_qr_login_overview_choose_account),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.steam_qr_login_overview_approve),
                    highlighted = true,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.steam_qr_login_challenge_title),
                description = stringResource(R.string.steam_qr_login_challenge_description),
            ) {
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
                OutlinedTextField(
                    value = challengeUrlInput,
                    onValueChange = { nextValue ->
                        if (nextValue != challengeUrlInput) {
                            authSessionInfo = null
                            errorMessage = null
                            statusMessage = null
                        }
                        challengeUrlInput = nextValue
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(stringResource(R.string.steam_qr_login_challenge_label))
                    },
                    supportingText = {
                        Text(stringResource(R.string.steam_qr_login_challenge_supporting))
                    },
                    minLines = 3,
                )
                OutlinedButton(
                    onClick = {
                        qrScanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setOrientationLocked(false)
                                .setBeepEnabled(false)
                                .setPrompt(context.getString(R.string.token_list_action_steam_qr_login)),
                        )
                    },
                    enabled = !isInspecting && !isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.token_list_action_steam_qr_login))
                }
                Button(
                    onClick = {
                        val candidate = selectedCandidate
                        val trimmedChallengeUrl = challengeUrlInput.trim()
                        if (trimmedChallengeUrl.isEmpty()) {
                            errorMessage = context.getString(R.string.steam_qr_login_url_required)
                            return@Button
                        }
                        if (candidate == null) {
                            errorMessage = context.getString(R.string.steam_qr_login_account_required)
                            return@Button
                        }
                        scope.launch {
                            isInspecting = true
                            errorMessage = null
                            try {
                                authSessionInfo = steamQrLoginApprovalManager.inspectAuthSession(
                                    token = candidate.token,
                                    session = candidate.session,
                                    challengeUrl = trimmedChallengeUrl,
                                )
                                statusMessage = context.getString(
                                    R.string.steam_qr_login_inspect_success,
                                    candidate.token.accountName,
                                )
                            } catch (error: Exception) {
                                authSessionInfo = null
                                errorMessage = error.message
                                    ?: context.getString(R.string.steam_qr_login_inspect_failed)
                            } finally {
                                isInspecting = false
                            }
                        }
                    },
                    enabled = !isInspecting && !isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isInspecting) {
                                R.string.steam_qr_login_inspect_loading
                            } else {
                                R.string.steam_qr_login_inspect_action
                            },
                        ),
                    )
                }
            }
        }
        when (val currentCandidatesState = candidatesState) {
            AppUiState.Loading -> item {
                Text(stringResource(R.string.steam_qr_login_accounts_loading))
            }

            AppUiState.Empty -> item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_qr_login_accounts_title),
                    description = stringResource(R.string.steam_qr_login_accounts_description),
                ) {
                    Text(
                        text = stringResource(R.string.steam_qr_login_accounts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is AppUiState.Error -> item {
                Text(
                    text = currentCandidatesState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is AppUiState.Success -> {
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_qr_login_accounts_title),
                        description = stringResource(R.string.steam_qr_login_accounts_description),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.steam_qr_login_accounts_count,
                                currentCandidatesState.value.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(currentCandidatesState.value, key = { it.token.id }) { candidate ->
                    val selected = candidate.token.id == selectedTokenId
                    ScreenSectionCard(
                        title = candidate.token.accountName,
                        description = stringResource(
                            R.string.steam_qr_login_account_steam_id,
                            candidate.session.steamId.orEmpty(),
                        ),
                        onClick = {
                            selectedTokenId = candidate.token.id
                            authSessionInfo = null
                            errorMessage = null
                            statusMessage = null
                        },
                    ) {
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_account_access_token_ready,
                            ),
                            highlighted = true,
                        )
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_account_session_updated_at,
                                candidate.session.updatedAt,
                            ),
                        )
                        Text(
                            text = stringResource(
                                if (selected) {
                                    R.string.steam_qr_login_account_selected
                                } else {
                                    R.string.steam_qr_login_account_select_action
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        authSessionInfo?.let { info ->
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.steam_qr_login_info_title),
                    description = stringResource(R.string.steam_qr_login_info_description),
                ) {
                    info.deviceFriendlyName?.let { deviceFriendlyName ->
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_info_device,
                                deviceFriendlyName,
                            ),
                            highlighted = true,
                        )
                    }
                    formatLocation(info)?.let { location ->
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_info_location,
                                location,
                            ),
                            highlighted = true,
                        )
                    }
                    info.ip?.let { ip ->
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_info_ip,
                                ip,
                            ),
                        )
                    }
                    ChecklistRow(
                        label = stringResource(
                            R.string.steam_qr_login_info_platform,
                            platformLabel(info.platformType),
                        ),
                    )
                    ChecklistRow(
                        label = stringResource(
                            R.string.steam_qr_login_info_version,
                            info.version,
                        ),
                    )
                    ChecklistRow(
                        label = stringResource(
                            R.string.steam_qr_login_info_client_id,
                            info.clientId.toString(),
                        ),
                    )
                    if (info.requestorLocationMismatch) {
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_info_risk_location_mismatch,
                            ),
                        )
                    }
                    if (info.highUsageLogin) {
                        ChecklistRow(
                            label = stringResource(
                                R.string.steam_qr_login_info_risk_high_usage,
                            ),
                        )
                    }
                    if (!info.requestorLocationMismatch && !info.highUsageLogin) {
                        ChecklistRow(
                            label = stringResource(R.string.steam_qr_login_info_risk_none),
                            highlighted = true,
                        )
                    }
                    Button(
                        onClick = {
                            val candidate = selectedCandidate ?: return@Button
                            scope.launch {
                                isResolving = true
                                errorMessage = null
                                try {
                                    steamQrLoginApprovalManager.resolveAuthSession(
                                        token = candidate.token,
                                        session = candidate.session,
                                        sessionInfo = info,
                                        approve = true,
                                    )
                                    statusMessage = context.getString(
                                        R.string.steam_qr_login_approve_success,
                                        candidate.token.accountName,
                                    )
                                } catch (error: Exception) {
                                    errorMessage = error.message
                                        ?: context.getString(R.string.steam_qr_login_action_failed)
                                } finally {
                                    isResolving = false
                                }
                            }
                        },
                        enabled = !isResolving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (isResolving) {
                                    R.string.steam_qr_login_action_loading
                                } else {
                                    R.string.steam_qr_login_approve_action
                                },
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val candidate = selectedCandidate ?: return@OutlinedButton
                            scope.launch {
                                isResolving = true
                                errorMessage = null
                                try {
                                    steamQrLoginApprovalManager.resolveAuthSession(
                                        token = candidate.token,
                                        session = candidate.session,
                                        sessionInfo = info,
                                        approve = false,
                                    )
                                    statusMessage = context.getString(
                                        R.string.steam_qr_login_reject_success,
                                        candidate.token.accountName,
                                    )
                                } catch (error: Exception) {
                                    errorMessage = error.message
                                        ?: context.getString(R.string.steam_qr_login_action_failed)
                                } finally {
                                    isResolving = false
                                }
                            }
                        },
                        enabled = !isResolving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_qr_login_reject_action))
                    }
                }
            }
        }
    }
}

private data class SteamQrLoginCandidate(
    val token: TokenRecord,
    val session: SteamSessionRecord,
)

private fun resolveCandidateAccessToken(session: SteamSessionRecord): String? {
    return session.accessToken?.trim()?.takeIf { it.isNotEmpty() }
        ?: session.oauthToken?.trim()?.takeIf { it.isNotEmpty() }
}

@Composable
private fun platformLabel(platformType: Int?): String {
    return stringResource(
        when (platformType) {
            2 -> R.string.steam_qr_login_platform_web_browser
            3 -> R.string.steam_qr_login_platform_mobile_app
            else -> R.string.steam_qr_login_platform_unknown
        },
    )
}

private fun formatLocation(info: SteamQrLoginAuthSessionInfo): String? {
    val segments = listOfNotNull(
        info.city?.takeIf { it.isNotBlank() },
        info.state?.takeIf { it.isNotBlank() },
        info.country?.takeIf { it.isNotBlank() },
    )
    return if (segments.isNotEmpty()) {
        segments.joinToString(separator = ", ")
    } else {
        info.geoloc?.takeIf { it.isNotBlank() }
    }
}
