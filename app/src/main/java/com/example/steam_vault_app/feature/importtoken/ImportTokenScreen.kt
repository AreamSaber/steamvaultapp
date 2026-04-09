package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard

private data class ImportMethod(
    val title: String,
    val body: String,
)

@Composable
fun ImportTokenScreen(
    onOpenSteamAddAuthenticator: () -> Unit,
    onOpenSteamBrowserLogin: () -> Unit,
    onSaveImport: (rawPayload: String, accountName: String, sharedSecret: String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    entryContext: ImportTokenEntryContext? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var rawPayload by rememberSaveable { mutableStateOf("") }
    var manualAccountName by rememberSaveable(
        entryContext?.kind,
        entryContext?.preferredAccountName,
        entryContext?.steamId,
    ) {
        mutableStateOf(entryContext?.preferredAccountName.orEmpty())
    }
    var manualSharedSecret by rememberSaveable { mutableStateOf("") }
    var helperMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scaffoldJson = entryContext?.let(ImportTokenScaffoldFactory::buildExistingAuthenticatorJson)

    val methods = listOf(
        ImportMethod(
            title = stringResource(R.string.import_method_mafile_title),
            body = stringResource(R.string.import_method_mafile_body),
        ),
        ImportMethod(
            title = stringResource(R.string.import_method_json_title),
            body = stringResource(R.string.import_method_json_body),
        ),
        ImportMethod(
            title = stringResource(R.string.import_method_otpauth_title),
            body = stringResource(R.string.import_method_otpauth_body),
        ),
        ImportMethod(
            title = stringResource(R.string.import_method_manual_title),
            body = stringResource(R.string.import_method_manual_body),
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (entryContext?.kind == ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR) {
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.import_existing_authenticator_title),
                    description = stringResource(
                        R.string.import_existing_authenticator_description,
                    ),
                ) {
                    entryContext.preferredAccountName?.let { accountName ->
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_account_name,
                                accountName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    entryContext.steamId?.let { steamId ->
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_steam_id,
                                steamId,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entryContext.deviceId?.let { deviceId ->
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_device_id,
                                deviceId,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entryContext.preferredAccountName != null) {
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_prefill_note,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ChecklistRow(
                        label = stringResource(
                            R.string.import_existing_authenticator_check_materials,
                        ),
                        highlighted = true,
                    )
                    ChecklistRow(
                        label = stringResource(
                            R.string.import_existing_authenticator_check_not_reissued,
                        ),
                        highlighted = false,
                    )
                    ChecklistRow(
                        label = stringResource(
                            R.string.import_existing_authenticator_check_recover,
                        ),
                        highlighted = false,
                    )
                    scaffoldJson?.let { scaffold ->
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_scaffold_title,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.import_existing_authenticator_scaffold_description,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = scaffold,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(
                                        R.string.import_existing_authenticator_scaffold_label,
                                    ),
                                )
                            },
                            readOnly = true,
                            minLines = 8,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(scaffold))
                                helperMessage = context.getString(
                                    R.string.import_existing_authenticator_scaffold_copied,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.import_existing_authenticator_scaffold_copy_action,
                                ),
                            )
                        }
                    }
                    helperMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.import_login_path_title),
                description = stringResource(R.string.import_login_path_description),
            ) {
                ChecklistRow(
                    label = stringResource(R.string.import_login_path_check_sign_in),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.import_login_path_check_verify),
                    highlighted = true,
                )
                ChecklistRow(
                    label = stringResource(R.string.import_login_path_check_bind_authenticator),
                    highlighted = true,
                )
                Button(
                    onClick = onOpenSteamAddAuthenticator,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_login_path_action))
                }
                OutlinedButton(
                    onClick = onOpenSteamBrowserLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_login_path_browser_fallback_action))
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.import_fallback_title),
                description = stringResource(R.string.import_fallback_description),
            ) {
                Text(
                    text = stringResource(R.string.import_fallback_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.import_supported_methods_title),
                description = stringResource(R.string.import_supported_methods_description),
            ) {
                methods.forEachIndexed { index, method ->
                    ChecklistRow(
                        label = stringResource(
                            R.string.import_method_summary,
                            method.title,
                            method.body,
                        ),
                        highlighted = index == 0,
                    )
                }
            }
        }
        items(methods) { method ->
            ScreenSectionCard(
                title = method.title,
                description = method.body,
            ) {
                Text(
                    text = stringResource(R.string.import_method_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.import_form_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                OutlinedTextField(
                    value = rawPayload,
                    onValueChange = { rawPayload = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text(stringResource(R.string.import_label_raw_payload)) },
                    placeholder = { Text(stringResource(R.string.import_placeholder_raw_payload)) },
                )
                OutlinedTextField(
                    value = manualAccountName,
                    onValueChange = { manualAccountName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.import_label_manual_account_name)) },
                )
                OutlinedTextField(
                    value = manualSharedSecret,
                    onValueChange = { manualSharedSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.import_label_manual_shared_secret)) },
                )
                Button(
                    onClick = {
                        onSaveImport(
                            rawPayload,
                            manualAccountName,
                            manualSharedSecret,
                        )
                    },
                    enabled = !isSubmitting && (
                        rawPayload.isNotBlank() ||
                            (manualAccountName.isNotBlank() && manualSharedSecret.isNotBlank())
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSubmitting) {
                                R.string.import_action_loading
                            } else {
                                R.string.import_action_idle
                            },
                        ),
                    )
                }
            }
        }
    }
}
