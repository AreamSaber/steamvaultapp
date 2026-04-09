package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.steam_vault_app.ui.common.ScreenSectionCard

@OptIn(ExperimentalMaterial3Api::class)
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
    var showManualForm by rememberSaveable { mutableStateOf(false) }

    val scaffoldJson = entryContext?.let(ImportTokenScaffoldFactory::buildExistingAuthenticatorJson)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.route_title_steam_add_authenticator),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Recovery / Existing Authenticator Alert
        if (entryContext?.kind == ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = stringResource(R.string.import_existing_authenticator_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_existing_authenticator_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        scaffoldJson?.let { scaffold ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(scaffold))
                                    helperMessage = context.getString(R.string.import_existing_authenticator_scaffold_copied)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.import_existing_authenticator_scaffold_copy_action))
                            }
                        }
                        helperMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Selection Cards
        item {
            Card(
                onClick = onOpenSteamAddAuthenticator,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Steam",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = stringResource(R.string.import_login_path_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.import_login_path_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = { showManualForm = !showManualForm },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "Code",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = stringResource(R.string.import_fallback_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.import_fallback_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showManualForm || errorMessage != null) {
            errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            item {
                ScreenSectionCard(
                    title = stringResource(R.string.import_form_title),
                    description = stringResource(R.string.import_fallback_note),
                ) {
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
                            onSaveImport(rawPayload, manualAccountName, manualSharedSecret)
                        },
                        enabled = !isSubmitting && (
                            rawPayload.isNotBlank() ||
                                (manualAccountName.isNotBlank() && manualSharedSecret.isNotBlank())
                            ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(
                                if (isSubmitting) R.string.import_action_loading else R.string.import_action_idle
                            ),
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenSteamBrowserLogin,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.import_login_path_browser_fallback_action))
            }
        }
    }
}
