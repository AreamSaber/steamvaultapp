package com.example.steam_vault_app.feature.steamconfirmations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamConfirmation
import com.example.steam_vault_app.domain.model.SteamSessionRecord
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.model.SteamGuardAccountSnapshot
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.sync.SteamConfirmationSyncManager
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun SteamConfirmationsScreen(
    tokenId: String,
    vaultRepository: VaultRepository,
    steamSessionRepository: SteamSessionRepository,
    steamConfirmationSyncManager: SteamConfirmationSyncManager,
    steamTimeSyncState: SteamTimeSyncState,
    onSyncSteamTime: () -> Unit,
    onOpenSteamSession: () -> Unit,
    refreshVersion: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshNonce by rememberSaveable { mutableStateOf(0) }
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    var activeActionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationsState by remember(tokenId) {
        mutableStateOf<AppUiState<List<SteamConfirmation>>>(AppUiState.Loading)
    }

    val screenState by produceState<AppUiState<SteamConfirmationsScreenState>>(
        initialValue = AppUiState.Loading,
        key1 = tokenId,
        key2 = refreshVersion,
    ) {
        value = try {
            val token = vaultRepository.getToken(tokenId)
            if (token == null) {
                AppUiState.Error(context.getString(R.string.steam_confirmation_token_not_found))
            } else {
                val session = steamSessionRepository.getSession(tokenId)
                val snapshot = SteamGuardAccountSnapshot.fromLegacy(
                    token = token,
                    session = session,
                )
                AppUiState.Success(
                    SteamConfirmationsScreenState(
                        snapshot = snapshot,
                    ),
                )
            }
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.steam_confirmation_list_failed_generic))
        }
    }

    suspend fun reloadConfirmations() {
        confirmationsState = AppUiState.Loading
        try {
            val confirmations = steamConfirmationSyncManager.fetchConfirmations(tokenId)
            confirmationsState = if (confirmations.isEmpty()) {
                AppUiState.Empty
            } else {
                AppUiState.Success(confirmations)
            }
        } catch (error: Exception) {
            confirmationsState = AppUiState.Error(
                error.message ?: context.getString(R.string.steam_confirmation_list_failed_generic),
            )
        }
    }

    LaunchedEffect(tokenId, refreshVersion, refreshNonce) {
        reloadConfirmations()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val currentScreenState = screenState) {
            AppUiState.Loading -> item {
                Text(stringResource(R.string.steam_confirmation_loading))
            }

            AppUiState.Empty -> Unit

            is AppUiState.Error -> item {
                Text(
                    text = currentScreenState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is AppUiState.Success -> {
                val snapshot = currentScreenState.value.snapshot
                val token = snapshot.toTokenRecord()

                item {
                    Text(
                        text = stringResource(R.string.steam_confirmation_title_for_account, token.accountName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_confirmation_prerequisites_title),
                        description = stringResource(R.string.steam_confirmation_prerequisites_description),
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
                            label = if (snapshot.hasProtocolSession || snapshot.hasWebConfirmationSession) {
                                stringResource(R.string.steam_confirmation_session_present)
                            } else {
                                stringResource(R.string.steam_confirmation_session_missing)
                            },
                            highlighted = snapshot.hasProtocolSession || snapshot.hasWebConfirmationSession,
                        )
                        if (!snapshot.hasProtocolSession) {
                            Text(
                                text = "This authenticator uses legacy web sessions. Please login via protocol to upgrade.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
                        if (
                            !token.identitySecret.isNullOrBlank() &&
                            !token.deviceId.isNullOrBlank() &&
                            (!snapshot.hasProtocolSession && !snapshot.hasWebConfirmationSession)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.steam_confirmation_session_repair_hint_sda,
                                    token.accountName,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Button(
                            onClick = onSyncSteamTime,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.steam_session_sync_time_action))
                        }
                        OutlinedButton(
                            onClick = onOpenSteamSession,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.steam_confirmation_open_session_action))
                        }
                    }
                }

                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_confirmation_list_title),
                        description = stringResource(R.string.steam_confirmation_list_description),
                    ) {
                        actionMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        actionError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (!snapshot.hasProtocolSession && !snapshot.hasWebConfirmationSession) {
                                    actionError = context.getString(R.string.steam_confirmation_error_no_session)
                                    return@OutlinedButton
                                }
                                actionMessage = null
                                actionError = null
                                refreshNonce += 1
                            },
                            enabled = activeActionKey == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.steam_confirmation_refresh_action))
                        }
                    }
                }

                when (val currentConfirmationsState = confirmationsState) {
                    AppUiState.Loading -> {
                        item {
                            Text(
                                text = stringResource(R.string.steam_confirmation_loading),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    AppUiState.Empty -> {
                        item {
                            ScreenSectionCard(
                                title = stringResource(R.string.steam_confirmation_empty_title),
                                description = stringResource(R.string.steam_confirmation_empty_description),
                            ) {}
                        }
                    }

                    is AppUiState.Error -> {
                        item {
                            Text(
                                text = currentConfirmationsState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    is AppUiState.Success -> {
                        items(
                            count = currentConfirmationsState.value.size,
                            key = { index -> currentConfirmationsState.value[index].id },
                        ) { index ->
                            val confirmation = currentConfirmationsState.value[index]
                            val approvingKey = "${confirmation.id}:allow"
                            val rejectingKey = "${confirmation.id}:cancel"
                            ScreenSectionCard(
                                title = confirmation.headline,
                                description = stringResource(
                                    R.string.steam_confirmation_type_line,
                                    formatConfirmationType(context, confirmation.typeCode),
                                ),
                            ) {
                                confirmation.summary.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                confirmation.warn?.let { warn ->
                                    Text(
                                        text = warn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.steam_confirmation_created_at,
                                        formatConfirmationTimestamp(confirmation.creationTimeEpochSeconds),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.steam_confirmation_nonce_line,
                                        maskValue(context, confirmation.nonce),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            activeActionKey = approvingKey
                                            actionMessage = null
                                            actionError = null
                                            try {
                                                val nextItems = steamConfirmationSyncManager.approveConfirmation(
                                                    tokenId = tokenId,
                                                    confirmationId = confirmation.id,
                                                    confirmationNonce = confirmation.nonce
                                                )
                                                confirmationsState = if (nextItems.isEmpty()) {
                                                    AppUiState.Empty
                                                } else {
                                                    AppUiState.Success(nextItems)
                                                }
                                                actionMessage = context.getString(
                                                    R.string.steam_confirmation_approve_success,
                                                    confirmation.headline,
                                                )
                                            } catch (error: Exception) {
                                                actionError = error.message
                                                    ?: context.getString(R.string.steam_confirmation_action_failed_generic)
                                            } finally {
                                                activeActionKey = null
                                            }
                                        }
                                    },
                                    enabled = activeActionKey == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.steam_confirmation_approve_action))
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            activeActionKey = rejectingKey
                                            actionMessage = null
                                            actionError = null
                                            try {
                                                val nextItems = steamConfirmationSyncManager.rejectConfirmation(
                                                    tokenId = tokenId,
                                                    confirmationId = confirmation.id,
                                                    confirmationNonce = confirmation.nonce,
                                                )
                                                confirmationsState = if (nextItems.isEmpty()) {
                                                    AppUiState.Empty
                                                } else {
                                                    AppUiState.Success(nextItems)
                                                }
                                                actionMessage = context.getString(
                                                    R.string.steam_confirmation_reject_success,
                                                    confirmation.headline,
                                                )
                                            } catch (error: Exception) {
                                                actionError = error.message
                                                    ?: context.getString(R.string.steam_confirmation_action_failed_generic)
                                            } finally {
                                                activeActionKey = null
                                            }
                                        }
                                    },
                                    enabled = activeActionKey == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.steam_confirmation_reject_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SteamConfirmationsScreenState(
    val snapshot: SteamGuardAccountSnapshot,
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

private fun formatConfirmationType(
    context: android.content.Context,
    typeCode: Int?,
): String {
    return when (typeCode) {
        2 -> context.getString(R.string.steam_confirmation_type_trade)
        3 -> context.getString(R.string.steam_confirmation_type_market)
        null -> context.getString(R.string.steam_confirmation_type_unknown)
        else -> context.getString(R.string.steam_confirmation_type_other, typeCode)
    }
}

private fun formatConfirmationTimestamp(
    epochSeconds: Long?,
): String {
    if (epochSeconds == null) {
        return "?"
    }
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
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
