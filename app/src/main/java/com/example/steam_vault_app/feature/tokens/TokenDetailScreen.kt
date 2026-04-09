package com.example.steam_vault_app.feature.tokens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultSensitiveValueRow

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
                AppUiState.Success(
                    SteamGuardAccountSnapshot.fromLegacy(
                        token = token,
                        session = session,
                    ),
                )
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (val currentState = tokenState) {
            AppUiState.Loading -> item {
                VaultInlineBanner(
                    text = stringResource(R.string.token_detail_loading),
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
                val snapshotItem = currentState.value
                val token = snapshotItem.toTokenRecord()
                val snapshot = buildSteamCodeSnapshot(
                    token = token,
                    currentEpochSeconds = currentEpochSeconds,
                    vaultCryptography = vaultCryptography,
                    errorCodeDisplay = context.getString(R.string.token_code_error_display),
                )

                item {
                    VaultPageHeader(
                        eyebrow = stringResource(R.string.vault_brand_label),
                        title = token.accountName,
                        subtitle = stringResource(R.string.token_detail_modern_subtitle),
                    )
                }
                copyMessage?.let { message ->
                    item {
                        VaultInlineBanner(
                            text = message,
                            tone = VaultBannerTone.Success,
                        )
                    }
                }
                if (snapshot.hasSecretError) {
                    item {
                        VaultInlineBanner(
                            text = stringResource(R.string.token_card_secret_error),
                            tone = VaultBannerTone.Error,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_modern_code_title),
                        description = stringResource(R.string.token_detail_modern_code_body),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                progress = { snapshot.progress },
                                modifier = Modifier.size(180.dp),
                                strokeWidth = 8.dp,
                                color = if (snapshot.secondsRemaining <= 5) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = snapshot.codeDisplay,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.token_detail_modern_time_remaining,
                                        snapshot.secondsRemaining,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        VaultPrimaryButton(
                            text = stringResource(R.string.vault_copy_action),
                            onClick = {
                                if (!snapshot.hasSecretError) {
                                    clipboardManager.setText(AnnotatedString(snapshot.codeDisplay))
                                    copyMessage = context.getString(R.string.token_detail_modern_copy_success)
                                }
                            },
                            enabled = !snapshot.hasSecretError,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_modern_session_title),
                        description = stringResource(R.string.token_detail_modern_session_body),
                        onClick = onOpenSteamSession,
                    ) {
                        VaultSecondaryButton(
                            text = stringResource(R.string.token_detail_modern_session_open),
                            onClick = onOpenSteamSession,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_modern_confirmations_title),
                        description = stringResource(R.string.token_detail_modern_confirmations_body),
                        onClick = onOpenSteamConfirmations,
                    ) {
                        VaultSecondaryButton(
                            text = stringResource(R.string.token_detail_modern_confirmations_open),
                            onClick = onOpenSteamConfirmations,
                        )
                    }
                }
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_detail_modern_advanced_title),
                        description = stringResource(R.string.token_detail_modern_advanced_body),
                    ) {
                        VaultSecondaryButton(
                            text = stringResource(
                                if (advancedInfoVisible) {
                                    R.string.vault_hide_advanced_action
                                } else {
                                    R.string.vault_show_advanced_action
                                },
                            ),
                            onClick = { advancedInfoVisible = !advancedInfoVisible },
                        )
                    }
                }
                item {
                    AnimatedVisibility(visible = advancedInfoVisible) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ScreenSectionCard(
                                title = stringResource(R.string.token_detail_metadata_title),
                                description = stringResource(R.string.token_detail_metadata_description),
                            ) {
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_platform),
                                    value = token.platform,
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_created_at),
                                    value = token.createdAt,
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_updated_at),
                                    value = token.updatedAt,
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_serial_number),
                                    value = token.serialNumber ?: stringResource(R.string.common_not_saved),
                                )
                                DetailLine(
                                    label = stringResource(R.string.token_detail_metadata_uri),
                                    value = token.uri ?: stringResource(R.string.common_not_saved),
                                )
                            }
                            ScreenSectionCard(
                                title = stringResource(R.string.token_detail_protected_title),
                                description = stringResource(R.string.token_detail_modern_sensitive_note),
                            ) {
                                SensitiveField(
                                    label = stringResource(R.string.token_detail_shared_secret_label),
                                    value = token.sharedSecret,
                                    clipboardManager = clipboardManager,
                                    context = context,
                                )
                                SensitiveField(
                                    label = stringResource(R.string.token_detail_identity_secret_label),
                                    value = token.identitySecret,
                                    clipboardManager = clipboardManager,
                                    context = context,
                                )
                                SensitiveField(
                                    label = stringResource(R.string.token_detail_metadata_device_id),
                                    value = token.deviceId,
                                    clipboardManager = clipboardManager,
                                    context = context,
                                )
                                SensitiveField(
                                    label = stringResource(R.string.token_detail_revocation_code_label),
                                    value = token.revocationCode,
                                    clipboardManager = clipboardManager,
                                    context = context,
                                    monospace = false,
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
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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

@Composable
private fun SensitiveField(
    label: String,
    value: String?,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    monospace: Boolean = true,
) {
    VaultSensitiveValueRow(
        label = label,
        value = value ?: context.getString(R.string.common_not_saved),
        copyDescription = context.getString(R.string.vault_copy_value_description),
        monospace = monospace,
        onCopy = value?.takeIf { it.isNotBlank() }?.let { currentValue ->
            {
                clipboardManager.setText(AnnotatedString(currentValue))
            }
        },
    )
}
