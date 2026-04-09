package com.example.steam_vault_app.feature.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultTextField

@Composable
fun ChangePasswordScreen(
    onPasswordChanged: (String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    val hasLength = password.length >= 10
    val hasMixedInput = password.any(Char::isDigit) && password.any(Char::isLetter)
    val matches = password.isNotBlank() && password == confirmation

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.change_password_title),
                subtitle = stringResource(R.string.change_password_description),
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.change_password_requirements_title),
                description = stringResource(R.string.change_password_requirements_description),
            ) {
                ChecklistRow(
                    label = stringResource(R.string.create_password_requirement_length),
                    highlighted = hasLength,
                )
                ChecklistRow(
                    label = stringResource(R.string.create_password_requirement_mixed),
                    highlighted = hasMixedInput,
                )
                ChecklistRow(
                    label = stringResource(R.string.create_password_requirement_match),
                    highlighted = matches,
                )
                ChecklistRow(
                    label = stringResource(R.string.change_password_requirement_biometric_reset),
                    highlighted = true,
                )
            }
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
            ScreenSectionCard(
                title = stringResource(R.string.change_password_title),
                description = stringResource(R.string.change_password_description),
            ) {
                VaultTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.label_new_master_password),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                )
                VaultTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = stringResource(R.string.label_confirm_new_password),
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
                            R.string.change_password_action_loading
                        } else {
                            R.string.change_password_action_idle
                        },
                    ),
                    onClick = { onPasswordChanged(password) },
                    enabled = hasLength && hasMixedInput && matches && !isSubmitting,
                )
            }
        }
    }
}
