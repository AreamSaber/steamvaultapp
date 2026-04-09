package com.example.steam_vault_app.feature.tokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.TokenRecord
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.domain.security.VaultCryptography
import com.example.steam_vault_app.ui.common.AppUiState
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton

@Composable
fun TokenListScreen(
    vaultRepository: VaultRepository,
    vaultCryptography: VaultCryptography,
    steamTimeOffsetSeconds: Long,
    refreshVersion: Int,
    onAddAccount: () -> Unit,
    onOpenSteamQrLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTokenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by produceState<AppUiState<List<TokenRecord>>>(
        initialValue = AppUiState.Loading,
        key1 = vaultRepository,
        key2 = refreshVersion,
    ) {
        value = try {
            val tokens = vaultRepository.getTokens()
            if (tokens.isEmpty()) {
                AppUiState.Empty
            } else {
                AppUiState.Success(tokens)
            }
        } catch (_: Exception) {
            AppUiState.Error(context.getString(R.string.token_list_load_failed))
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
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.token_list_modern_title),
                subtitle = stringResource(R.string.token_list_modern_body),
            )
        }
        when (val currentState = state) {
            AppUiState.Loading -> {
                item {
                    VaultInlineBanner(
                        text = stringResource(R.string.token_list_loading),
                        tone = VaultBannerTone.Neutral,
                    )
                }
            }

            AppUiState.Empty -> {
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_list_modern_empty_title),
                        description = stringResource(R.string.token_list_modern_empty_body),
                    ) {
                        VaultPrimaryButton(
                            text = stringResource(R.string.token_list_modern_empty_primary),
                            onClick = onAddAccount,
                        )
                        VaultSecondaryButton(
                            text = stringResource(R.string.token_list_modern_empty_secondary),
                            onClick = onOpenSteamQrLogin,
                        )
                    }
                }
            }

            is AppUiState.Error -> {
                item {
                    VaultInlineBanner(
                        text = currentState.message,
                        tone = VaultBannerTone.Error,
                    )
                }
            }

            is AppUiState.Success -> {
                items(currentState.value, key = { token -> token.id }) { token ->
                    VaultTokenCard(
                        token = token,
                        currentEpochSeconds = currentEpochSeconds,
                        vaultCryptography = vaultCryptography,
                        onOpenTokenDetails = onOpenTokenDetails,
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.token_list_modern_trust_title),
                description = stringResource(R.string.token_list_modern_trust_body),
            ) {
                VaultPrimaryButton(
                    text = stringResource(R.string.token_list_modern_empty_primary),
                    onClick = onAddAccount,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.token_list_modern_scan_login),
                    onClick = onOpenSteamQrLogin,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.token_list_modern_open_settings),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun VaultTokenCard(
    token: TokenRecord,
    currentEpochSeconds: Long,
    vaultCryptography: VaultCryptography,
    onOpenTokenDetails: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val copyActionText = stringResource(R.string.token_list_modern_copy)
    val snapshot = buildSteamCodeSnapshot(
        token = token,
        currentEpochSeconds = currentEpochSeconds,
        vaultCryptography = vaultCryptography,
        errorCodeDisplay = stringResource(R.string.token_code_error_display),
    )

    val isWarning = snapshot.secondsRemaining <= 5
    val primaryColor = if (isWarning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTokenDetails(token.id) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.token_list_modern_account_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = token.accountName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                
                // Progress Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { snapshot.progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 3.dp,
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(
                        text = "${snapshot.secondsRemaining}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                    )
                }
            }
            
            // Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = snapshot.codeDisplay,
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isWarning) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.clickable(enabled = !snapshot.hasSecretError) {
                        clipboardManager.setText(AnnotatedString(snapshot.codeDisplay))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = copyActionText,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = copyActionText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            
            if (snapshot.hasSecretError) {
                VaultInlineBanner(
                    text = stringResource(R.string.token_card_secret_error),
                    tone = VaultBannerTone.Error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
