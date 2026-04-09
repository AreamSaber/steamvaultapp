package com.example.steam_vault_app.feature.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard

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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.unlock_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.unlock_behavior_title),
                description = stringResource(R.string.unlock_behavior_description),
            ) {
                ChecklistRow(
                    label = stringResource(R.string.unlock_behavior_verify_password),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.unlock_behavior_resume_session),
                    highlighted = true,
                )
                ChecklistRow(label = stringResource(R.string.unlock_behavior_biometric_hint))
            }
        }
        errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_master_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                Button(
                    onClick = { onUnlock(password) },
                    enabled = password.isNotBlank() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSubmitting) {
                                R.string.unlock_action_loading
                            } else {
                                R.string.unlock_action_idle
                            },
                        ),
                    )
                }
                if (showBiometricUnlock) {
                    OutlinedButton(
                        onClick = onUnlockWithBiometric,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.unlock_action_biometric))
                    }
                }
                OutlinedButton(
                    onClick = onRestoreBackup,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.unlock_action_restore_backup))
                }
            }
        }
    }
}
