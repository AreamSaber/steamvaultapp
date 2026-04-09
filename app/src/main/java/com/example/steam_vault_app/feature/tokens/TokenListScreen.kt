package com.example.steam_vault_app.feature.tokens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.token_list_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.token_list_section_title),
                description = stringResource(R.string.token_list_section_description),
            ) {
                ChecklistRow(label = stringResource(R.string.token_list_check_generate), highlighted = true)
                ChecklistRow(label = stringResource(R.string.token_list_check_countdown), highlighted = true)
                ChecklistRow(label = stringResource(R.string.token_list_check_detail), highlighted = true)
            }
        }
        when (val currentState = state) {
            AppUiState.Loading -> {
                item { Text(stringResource(R.string.token_list_loading)) }
            }

            AppUiState.Empty -> {
                item {
                    ScreenSectionCard(
                        title = stringResource(R.string.token_list_empty_title),
                        description = stringResource(R.string.token_list_empty_description),
                    ) {
                        ChecklistRow(label = stringResource(R.string.token_list_empty_check_login))
                        ChecklistRow(label = stringResource(R.string.token_list_empty_check_import))
                    }
                }
            }

            is AppUiState.Error -> {
                item {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is AppUiState.Success -> {
                items(currentState.value, key = { token -> token.id }) { token ->
                    TokenCard(
                        token = token,
                        currentEpochSeconds = currentEpochSeconds,
                        vaultCryptography = vaultCryptography,
                        onOpenTokenDetails = onOpenTokenDetails,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAddAccount,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.token_list_action_import))
                }
                OutlinedButton(
                    onClick = onOpenSteamQrLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.token_list_action_steam_qr_login))
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.token_list_action_settings))
                }
            }
        }
    }
}

@Composable
private fun TokenCard(
    token: TokenRecord,
    currentEpochSeconds: Long,
    vaultCryptography: VaultCryptography,
    onOpenTokenDetails: (String) -> Unit,
) {
    val snapshot = buildSteamCodeSnapshot(
        token = token,
        currentEpochSeconds = currentEpochSeconds,
        vaultCryptography = vaultCryptography,
        errorCodeDisplay = stringResource(R.string.token_code_error_display),
    )

    ScreenSectionCard(
        title = token.accountName,
        description = stringResource(R.string.token_card_updated_at, token.updatedAt),
        onClick = { onOpenTokenDetails(token.id) },
    ) {
        Text(
            text = snapshot.codeDisplay,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.token_card_seconds_remaining, snapshot.secondsRemaining),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { snapshot.progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surface,
        )
        Text(
            text = stringResource(R.string.token_card_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (snapshot.hasSecretError) {
            Text(
                text = stringResource(R.string.token_card_secret_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
