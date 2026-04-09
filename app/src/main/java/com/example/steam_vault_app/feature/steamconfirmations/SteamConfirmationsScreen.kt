package com.example.steam_vault_app.feature.steamconfirmations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.example.steam_vault_app.domain.model.SteamGuardAccountSnapshot
import com.example.steam_vault_app.domain.model.SteamTimeSyncState
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.sync.SteamConfirmationSyncManager
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultKeyValueRow
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (val currentScreenState = screenState) {
            AppUiState.Loading -> item {
                VaultInlineBanner(
                    text = stringResource(R.string.steam_confirmation_loading),
                    tone = VaultBannerTone.Neutral,
                )
            }

            AppUiState.Empty -> Unit

            is AppUiState.Error -> item {
                VaultInlineBanner(
                    text = currentScreenState.message,
                    tone = VaultBannerTone.Error,
                )
            }

            is AppUiState.Success -> {
                val snapshot = currentScreenState.value.snapshot
                val token = snapshot.toTokenRecord()
                val hasSession = snapshot.hasProtocolSession || snapshot.hasWebConfirmationSession
                val hasSyncedTime = steamTimeSyncState.lastSyncAt != null

                item {
                    VaultPageHeader(
                        eyebrow = stringResource(R.string.vault_brand_label),
                        title = stringResource(R.string.steam_confirmation_title_for_account, token.accountName),
                        subtitle = stringResource(R.string.steam_confirmation_empty_description),
                    )
                }

                actionMessage?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Success,
                        )
                    }
                }
                actionError?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Error,
                        )
                    }
                }

                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.steam_confirmation_loading),
                        description = stringResource(R.string.steam_confirmation_empty_description),
                    ) {
                        VaultKeyValueRow(
                            label = stringResource(R.string.steam_session_open_confirmations_action),
                            value = if (hasSession) {
                                stringResource(R.string.steam_session_validation_status_success)
                            } else {
                                stringResource(R.string.steam_confirmation_session_missing)
                            },
                        )
                        VaultKeyValueRow(
                            label = stringResource(R.string.steam_session_sync_time_action),
                            value = if (hasSyncedTime) {
                                stringResource(R.string.steam_session_validation_status_success)
                            } else {
                                stringResource(R.string.steam_session_time_not_synced)
                            },
                        )
                    }
                }

                if (!hasSession) {
                    item {
                        ScreenSectionCard(
                            title = stringResource(R.string.steam_confirmation_session_missing),
                            description = stringResource(
                                R.string.steam_confirmation_session_repair_hint_sda,
                                token.accountName,
                            ),
                        ) {
                            VaultPrimaryButton(
                                text = stringResource(R.string.steam_confirmation_open_session_action),
                                onClick = onOpenSteamSession,
                            )
                        }
                    }
                } else if (!hasSyncedTime) {
                    item {
                        ScreenSectionCard(
                            title = stringResource(R.string.steam_session_time_not_synced),
                            description = stringResource(R.string.steam_session_sync_time_action),
                        ) {
                            VaultPrimaryButton(
                                text = stringResource(R.string.steam_session_sync_time_action),
                                onClick = onSyncSteamTime,
                            )
                        }
                    }
                } else {
                    item {
                        VaultPrimaryButton(
                            text = stringResource(R.string.steam_confirmation_refresh_action),
                            onClick = {
                                actionMessage = null
                                actionError = null
                                refreshNonce += 1
                            },
                            enabled = activeActionKey == null,
                        )
                    }

                    when (val currentConfirmationsState = confirmationsState) {
                        AppUiState.Loading -> item {
                            VaultInlineBanner(
                                text = stringResource(R.string.steam_confirmation_loading),
                                tone = VaultBannerTone.Neutral,
                            )
                        }

                        AppUiState.Empty -> item {
                            ScreenSectionCard(
                                title = stringResource(R.string.steam_confirmation_empty_title),
                                description = stringResource(R.string.steam_confirmation_empty_description),
                            ) {
                                VaultSecondaryButton(
                                    text = stringResource(R.string.steam_confirmation_refresh_action),
                                    onClick = { refreshNonce += 1 },
                                )
                            }
                        }

                        is AppUiState.Error -> item {
                            VaultInlineBanner(
                                text = currentConfirmationsState.message,
                                tone = VaultBannerTone.Error,
                            )
                        }

                        is AppUiState.Success -> {
                            items(
                                items = currentConfirmationsState.value,
                                key = { confirmation -> confirmation.id },
                            ) { confirmation ->
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
                                        VaultInlineBanner(
                                            text = warn,
                                            tone = VaultBannerTone.Warning,
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.steam_confirmation_created_at,
                                            formatConfirmationTimestamp(
                                                confirmation.creationTimeEpochSeconds,
                                            ),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    VaultPrimaryButton(
                                        text = stringResource(R.string.steam_confirmation_approve_action),
                                        onClick = {
                                            scope.launch {
                                                activeActionKey = approvingKey
                                                actionMessage = null
                                                actionError = null
                                                try {
                                                    val nextItems = steamConfirmationSyncManager.approveConfirmation(
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
                                    )
                                    VaultSecondaryButton(
                                        text = stringResource(R.string.steam_confirmation_reject_action),
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
                                    )
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
