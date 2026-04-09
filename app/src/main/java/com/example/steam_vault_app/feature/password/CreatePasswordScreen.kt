package com.example.steam_vault_app.feature.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
fun CreatePasswordScreen(
    onPasswordCreated: (String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    val hasLength = password.length >= 12
    val hasMixedInput = listOf(password.any(Char::isLowerCase), password.any(Char::isUpperCase), password.any(Char::isDigit), password.any { !it.isLetterOrDigit() }).count { it } >= 2
    val matches = password.isNotBlank() && password == confirmation

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                VaultPageHeader(
                    eyebrow = stringResource(R.string.vault_brand_label),
                    title = stringResource(R.string.create_password_modern_title),
                    subtitle = stringResource(R.string.create_password_modern_body),
                )
                Text(
                    text = stringResource(R.string.create_password_modern_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VaultTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.create_password_modern_field_password),
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
                    label = stringResource(R.string.create_password_modern_field_confirm),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }

        item {
            ScreenSectionCard(
                title = stringResource(R.string.create_password_modern_rule_title),
                description = stringResource(R.string.create_password_modern_rule_body),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChecklistRow(
                            label = stringResource(R.string.create_password_modern_rule_length),
                            highlighted = hasLength,
                        )
                        ChecklistRow(
                            label = stringResource(R.string.create_password_modern_rule_mix),
                            highlighted = hasMixedInput,
                        )
                        ChecklistRow(
                            label = stringResource(R.string.create_password_modern_rule_match),
                            highlighted = matches,
                        )
                    }
                }
            }
        }

        item {
            ScreenSectionCard(
                title = stringResource(R.string.create_password_modern_security_title),
                description = stringResource(R.string.create_password_modern_security_body),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.vault_status_local_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item {
            VaultPrimaryButton(
                text = stringResource(
                    if (isSubmitting) {
                        R.string.create_password_modern_action_loading
                    } else {
                        R.string.create_password_modern_action
                    },
                ),
                enabled = hasLength && hasMixedInput && matches && !isSubmitting,
                onClick = { onPasswordCreated(password) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
