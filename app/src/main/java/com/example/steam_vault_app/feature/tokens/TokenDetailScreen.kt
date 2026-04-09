package com.example.steam_vault_app.feature.tokens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard

@Composable
fun TokenDetailScreen(
    tokenId: String,
    vaultRepository: VaultRepository,
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
    var copyMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val tokenState by produceState<AppUiState<TokenRecord>>(
        initialValue = AppUiState.Loading,
        key1 = tokenId,
        key2 = refreshVersion,
    ) {
        value = try {
            val token = vaultRepository.getToken(tokenId)
            if (token == null) {
                AppUiState.Error(context.getString(R.string.token_detail_error_not_found))
            } else {
                AppUiState.Success(token)
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
                val token = currentState.value
                val snapshot = buildSteamCodeSnapshot(
                    token = token,
                    currentEpochSeconds = currentEpochSeconds,
                    vaultCryptography = vaultCryptography,
                    errorCodeDisplay = context.getString(R.string.token_code_error_display),
                )

                item {
                    Text(
                        text = token.accountName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_code_title),
                        description = stringResource(R.string.token_detail_code_description),
                    ) {
                        Text(
                            text = snapshot.codeDisplay,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.token_card_seconds_remaining,
                                snapshot.secondsRemaining,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LinearProgressIndicator(
                            progress = { snapshot.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.token_detail_copy_action))
                            }
                            OutlinedButton(
                                onClick = {
                                    sensitiveValuesVisible = !sensitiveValuesVisible
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                        }
                        copyMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (snapshot.hasSecretError) {
                            Text(
                                text = stringResource(R.string.token_card_secret_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_session_title),
                        description = stringResource(R.string.token_detail_session_description),
                    ) {
                        ChecklistRow(
                            label = if (token.identitySecret.isNullOrBlank()) {
                                stringResource(R.string.token_detail_identity_missing)
                            } else {
                                stringResource(R.string.token_detail_identity_present)
                            },
                            highlighted = !token.identitySecret.isNullOrBlank(),
                        )
                        Button(
                            onClick = onOpenSteamSession,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.token_detail_open_session_action))
                        }
                        OutlinedButton(
                            onClick = onOpenSteamConfirmations,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.token_detail_open_confirmations_action))
                        }
                    }
                }
                item {
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
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_protected_title),
                        description = stringResource(R.string.token_detail_protected_description),
                    ) {
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
