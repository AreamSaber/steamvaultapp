package com.example.steam_vault_app.feature.tokens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.SteamGuardAccountSnapshot
import com.example.steam_vault_app.domain.repository.SteamSessionRepository
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard

@Composable
fun TokenDetailScreen(
    tokenId: String,
    vaultRepository: VaultRepository,
    steamSessionRepository: SteamSessionRepository,
    vaultCryptography: VaultCryptography,
    steamTimeOffsetSeconds: Long,
    refreshVersion: Int,
    onOpenSteamSession: () -> Unit,
    onOpenSteamConfirmations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var sensitiveValuesVisible by rememberSaveable { mutableStateOf(false) }
    var advancedInfoVisible by rememberSaveable { mutableStateOf(false) }
    var copyMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val tokenState by produceState<AppUiState<SteamGuardAccountSnapshot>>(
        initialValue = AppUiState.Loading,
        key1 = tokenId,
        key2 = refreshVersion,
    ) {
        value = try {
            val token = vaultRepository.getToken(tokenId)
            if (token == null) {
                AppUiState.Error(context.getString(R.string.token_detail_error_not_found))
            } else {
                val session = steamSessionRepository.getSession(tokenId)
                val snapshot = SteamGuardAccountSnapshot.fromLegacy(
                    token = token,
                    session = session,
                )
                AppUiState.Success(snapshot)
            }
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.token_detail_error_load_failed))
        }
    }

    val currentEpochSeconds = rememberCurrentEpochSeconds(
        offsetSeconds = steamTimeOffsetSeconds,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val currentState = tokenState) {
            AppUiState.Loading -> {
                item { Text(stringResource(R.string.token_detail_loading)) }
            }

            AppUiState.Empty -> Unit

            is AppUiState.Error -> {
                item {
                    Text(
                        text = currentState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is AppUiState.Success -> {
                val snapshotItem = currentState.value
                val token = snapshotItem.toTokenRecord()
                val snapshot = buildSteamCodeSnapshot(
                    token = token,
                    currentEpochSeconds = currentEpochSeconds,
                    vaultCryptography = vaultCryptography,
                    errorCodeDisplay = context.getString(R.string.token_code_error_display),
                )

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = token.accountName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { snapshot.progress },
                                modifier = Modifier.size(160.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 8.dp,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = snapshot.codeDisplay,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.token_card_seconds_remaining,
                                        snapshot.secondsRemaining,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = {
                                if (!snapshot.hasSecretError) {
                                    clipboardManager.setText(
                                        AnnotatedString(snapshot.codeDisplay),
                                    )
                                    copyMessage = context.getString(R.string.token_detail_copy_success)
                                }
                            },
                            enabled = !snapshot.hasSecretError,
                            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                        ) {
                            Text(stringResource(R.string.token_detail_copy_action), style = MaterialTheme.typography.titleMedium)
                        }
                        
                        copyMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        
                        if (snapshot.hasSecretError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.token_card_secret_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSteamConfirmations,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.token_detail_open_confirmations_action))
                        }
                    }
                }
                
                item {
                    TextButton(
                        onClick = { advancedInfoVisible = !advancedInfoVisible },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (advancedInfoVisible) "Hide Advanced Info" else "Show Advanced Info")
                    }
                }

                item {
                    AnimatedVisibility(visible = advancedInfoVisible) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ScreenSectionCard(
                                title = stringResource(R.string.token_detail_session_title),
                                description = stringResource(R.string.token_detail_session_description),
                            ) {
                                ChecklistRow(
                                    label = if (snapshotItem.hasProtocolSession) {
                                        stringResource(R.string.steam_session_identity_present) 
                                    } else if (snapshotItem.hasWebConfirmationSession) {
                                        stringResource(R.string.steam_confirmation_session_cookie_present)
                                    } else {
                                        stringResource(R.string.steam_confirmation_session_missing)
                                    },
                                    highlighted = snapshotItem.hasProtocolSession || snapshotItem.hasWebConfirmationSession,
                                )
                                if (!snapshotItem.hasProtocolSession) {
                                    Text(
                                        text = "This authenticator uses legacy web sessions. Please login via protocol to upgrade.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Button(
                                    onClick = onOpenSteamSession,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.token_detail_open_session_action))
                                }
                            }

                            ScreenSectionCard(
                                title = stringResource(R.string.token_detail_metadata_title),
                                description = stringResource(R.string.token_detail_metadata_description),
                            ) {
                                DetailLine(label = stringResource(R.string.token_detail_metadata_platform), value = token.platform)
                                DetailLine(label = stringResource(R.string.token_detail_metadata_created_at), value = token.createdAt)
                                DetailLine(label = stringResource(R.string.token_detail_metadata_updated_at), value = token.updatedAt)
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_serial_number),
                                    value = token.serialNumber ?: stringResource(R.string.common_not_saved),
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_device_id),
                                    value = token.deviceId ?: stringResource(R.string.common_not_saved),
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_token_gid),
                                    value = token.tokenGid ?: stringResource(R.string.common_not_saved),
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_uri),
                                    value = token.uri ?: stringResource(R.string.common_not_saved),
                                )
                            }

                            ScreenSectionCard(
                                title = stringResource(R.string.token_detail_protected_title),
                                description = stringResource(R.string.token_detail_protected_description),
                            ) {
                                OutlinedButton(
                                    onClick = { sensitiveValuesVisible = !sensitiveValuesVisible },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            if (sensitiveValuesVisible) {
                                                R.string.token_detail_hide_sensitive
                                            } else {
                                                R.string.token_detail_show_sensitive
                                            },
                                        ),
                                    )
                                }
                                ChecklistRow(
                                    label = if (sensitiveValuesVisible) {
                                        stringResource(
                                            R.string.token_detail_revocation_code,
                                            token.revocationCode ?: stringResource(R.string.common_not_saved),
                                        )
                                    } else {
                                        stringResource(
                                            R.string.token_detail_revocation_code,
                                            maskValue(context, token.revocationCode),
                                        )
                                    },
                                    highlighted = sensitiveValuesVisible,
                                )
                                ChecklistRow(
                                    label = if (sensitiveValuesVisible) {
                                        stringResource(
                                            R.string.token_detail_identity_secret,
                                            token.identitySecret ?: stringResource(R.string.common_not_saved),
                                        )
                                    } else {
                                        stringResource(
                                            R.string.token_detail_identity_secret,
                                            maskValue(context, token.identitySecret),
                                        )
                                    },
                                    highlighted = sensitiveValuesVisible,
                                )
                                ChecklistRow(
                                    label = if (sensitiveValuesVisible) {
                                        stringResource(
                                            R.string.token_detail_secret1,
                                            token.secret1 ?: stringResource(R.string.common_not_saved),
                                        )
                                    } else {
                                        stringResource(
                                            R.string.token_detail_secret1,
                                            maskValue(context, token.secret1),
                                        )
                                    },
                                    highlighted = sensitiveValuesVisible,
                                )
                                ChecklistRow(
                                    label = if (sensitiveValuesVisible) {
                                        stringResource(R.string.token_detail_shared_secret, token.sharedSecret)
                                    } else {
                                        stringResource(
                                            R.string.token_detail_shared_secret,
                                            maskValue(context, token.sharedSecret),
                                        )
                                    },
                                    highlighted = sensitiveValuesVisible,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun maskValue(
    context: android.content.Context,
    value: String?,
): String {
    if (value.isNullOrBlank()) {
        return context.getString(R.string.common_hidden_or_not_saved)
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
