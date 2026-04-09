package com.example.steam_vault_app.feature.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
fun CreatePasswordScreen(
    onPasswordCreated: (String) -> Unit,
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.create_password_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Text(
                text = stringResource(R.string.create_password_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.create_password_requirements_title),
                description = stringResource(R.string.create_password_requirements_description),
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
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                Button(
                    onClick = { onPasswordCreated(password) },
                    enabled = hasLength && hasMixedInput && matches && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSubmitting) {
                                R.string.create_password_action_loading
                            } else {
                                R.string.create_password_action_idle
                            },
                        ),
                    )
                }
            }
        }
    }
}
