package com.example.steam_vault_app.feature.importtoken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultTextField

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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.import_modern_title),
                subtitle = stringResource(R.string.import_modern_body),
            )
        }

        if (entryContext?.kind == ImportTokenEntryContext.Kind.EXISTING_AUTHENTICATOR) {
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.import_modern_existing_title),
                    description = stringResource(R.string.import_modern_existing_body),
                ) {
                    scaffoldJson?.let { scaffold ->
                        helperMessage?.let { message ->
                            VaultInlineBanner(
                                text = message,
                                tone = VaultBannerTone.Success,
                            )
                        }
                        VaultSecondaryButton(
                            text = stringResource(R.string.import_existing_authenticator_scaffold_copy_action),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(scaffold))
                                helperMessage = context.getString(
                                    R.string.import_existing_authenticator_scaffold_copied,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            ScreenSectionCard(
                title = stringResource(R.string.import_modern_login_title),
                description = stringResource(R.string.import_modern_login_body),
            ) {
                VaultPrimaryButton(
                    text = stringResource(R.string.import_modern_login_action),
                    onClick = onOpenSteamAddAuthenticator,
                )
            }
        }

        item {
            ScreenSectionCard(
                title = stringResource(R.string.import_modern_manual_title),
                description = stringResource(R.string.import_modern_manual_body),
            ) {
                VaultSecondaryButton(
                    text = stringResource(
                        if (showManualForm) {
                            R.string.import_modern_manual_hide_action
                        } else {
                            R.string.import_modern_manual_show_action
                        },
                    ),
                    onClick = { showManualForm = !showManualForm },
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

        if (showManualForm || errorMessage != null) {
            item {
                ScreenSectionCard(
                    title = stringResource(R.string.import_modern_form_title),
                    description = stringResource(R.string.import_modern_form_body),
                ) {
                    VaultTextField(
                        value = rawPayload,
                        onValueChange = { rawPayload = it },
                        label = stringResource(R.string.import_modern_field_payload),
                        placeholder = stringResource(R.string.import_modern_field_payload_placeholder),
                        minLines = 4,
                    )
                    VaultTextField(
                        value = manualAccountName,
                        onValueChange = { manualAccountName = it },
                        label = stringResource(R.string.import_modern_field_account),
                    )
                    VaultTextField(
                        value = manualSharedSecret,
                        onValueChange = { manualSharedSecret = it },
                        label = stringResource(R.string.import_modern_field_secret),
                    )
                    VaultPrimaryButton(
                        text = stringResource(
                            if (isSubmitting) {
                                R.string.import_modern_save_action_loading
                            } else {
                                R.string.import_modern_save_action
                            },
                        ),
                        onClick = {
                            onSaveImport(rawPayload, manualAccountName, manualSharedSecret)
                        },
                        enabled = !isSubmitting && (
                            rawPayload.isNotBlank() ||
                                (manualAccountName.isNotBlank() && manualSharedSecret.isNotBlank())
                            ),
                    )
                }
            }
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VaultSecondaryButton(
                    text = stringResource(R.string.import_modern_browser_fallback),
                    onClick = onOpenSteamBrowserLogin,
                )
                Text(
                    text = stringResource(R.string.import_modern_footer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
