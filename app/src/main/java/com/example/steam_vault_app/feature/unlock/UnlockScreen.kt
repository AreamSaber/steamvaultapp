package com.example.steam_vault_app.feature.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = CircleShape,
                )
                .align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.unlock_modern_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.unlock_modern_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (showBiometricUnlock) {
                Spacer(modifier = Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = CircleShape,
                        )
                        .clickable(enabled = !isSubmitting, onClick = onUnlockWithBiometric),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.unlock_modern_biometric_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.unlock_modern_secondary_biometric),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }

            errorMessage?.let { message ->
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

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
            Spacer(modifier = Modifier.height(14.dp))
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

            Spacer(modifier = Modifier.height(26.dp))
            TextButton(
                onClick = onRestoreBackup,
                enabled = !isSubmitting,
            ) {
                Text(
                    text = stringResource(R.string.unlock_modern_secondary_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
