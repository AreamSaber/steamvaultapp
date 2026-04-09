package com.example.steam_vault_app.feature.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultTextField

@Composable
fun UnlockScreen(
    onUnlock: (String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    onRestoreBackup: () -> Unit,
    showBiometricUnlock: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.unlock_modern_title),
                subtitle = stringResource(R.string.unlock_modern_body),
            )
        }
        item {
            VaultInlineBanner(
                text = stringResource(
                    if (showBiometricUnlock) {
                        R.string.unlock_modern_hint_biometric
                    } else {
                        R.string.unlock_modern_hint_password
                    },
                ),
                tone = VaultBannerTone.Neutral,
            )
        }
        errorMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Error,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VaultTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.unlock_modern_field_password),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                )
                VaultPrimaryButton(
                    text = stringResource(
                        if (isSubmitting) {
                            R.string.unlock_modern_primary_loading
                        } else {
                            R.string.unlock_modern_primary
                        },
                    ),
                    onClick = { onUnlock(password) },
                    enabled = password.isNotBlank() && !isSubmitting,
                )
                if (showBiometricUnlock) {
                    VaultSecondaryButton(
                        text = stringResource(R.string.unlock_modern_secondary_biometric),
                        onClick = onUnlockWithBiometric,
                        enabled = !isSubmitting,
                    )
                }
                VaultSecondaryButton(
                    text = stringResource(R.string.unlock_modern_secondary_restore),
                    onClick = onRestoreBackup,
                    enabled = !isSubmitting,
                )
            }
        }
    }
}
