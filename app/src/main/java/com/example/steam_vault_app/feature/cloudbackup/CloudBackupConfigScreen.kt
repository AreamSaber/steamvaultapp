package com.example.steam_vault_app.feature.cloudbackup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.domain.model.WebDavBackupConfiguration
import com.example.steam_vault_app.domain.repository.CloudBackupRepository
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton
import com.example.steam_vault_app.ui.common.VaultTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_JIANGUOYUN_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"

@Composable
fun CloudBackupConfigScreen(
    cloudBackupRepository: CloudBackupRepository,
    onConfigurationSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serviceUrl by rememberSaveable { mutableStateOf(DEFAULT_JIANGUOYUN_WEBDAV_URL) }
    var username by rememberSaveable { mutableStateOf("") }
    var appPassword by rememberSaveable { mutableStateOf("") }
    var remotePath by rememberSaveable {
        mutableStateOf(WebDavBackupConfiguration.DEFAULT_REMOTE_PATH)
    }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(cloudBackupRepository) {
        isLoading = true
        errorMessage = null
        try {
            val existing = withContext(Dispatchers.IO) {
                cloudBackupRepository.getConfiguration()
            }
            if (existing != null) {
                serviceUrl = existing.serverUrl
                username = existing.username
                appPassword = existing.appPassword
                remotePath = existing.remotePath
            }
        } catch (error: Exception) {
            errorMessage = error.message ?: context.getString(R.string.cloud_backup_config_read_failed)
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VaultPageHeader(
                eyebrow = stringResource(R.string.vault_brand_label),
                title = stringResource(R.string.cloud_config_modern_title),
                subtitle = stringResource(R.string.cloud_config_modern_body),
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
        statusMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Success,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.cloud_config_modern_card_title),
                description = stringResource(R.string.cloud_config_modern_card_body),
            ) {
                VaultTextField(
                    value = serviceUrl,
                    onValueChange = { serviceUrl = it },
                    label = stringResource(R.string.cloud_config_modern_url),
                    singleLine = true,
                    enabled = !isLoading && !isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                VaultTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.cloud_config_modern_username),
                    singleLine = true,
                    enabled = !isLoading && !isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                VaultTextField(
                    value = appPassword,
                    onValueChange = { appPassword = it },
                    label = stringResource(R.string.cloud_config_modern_password),
                    singleLine = true,
                    enabled = !isLoading && !isSaving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
                VaultTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    label = stringResource(R.string.cloud_config_modern_path),
                    singleLine = true,
                    enabled = !isLoading && !isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.cloud_config_modern_tip),
                description = stringResource(R.string.cloud_config_modern_status_waiting),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VaultSecondaryButton(
                        text = stringResource(R.string.cloud_config_modern_restore_template),
                        onClick = {
                            serviceUrl = DEFAULT_JIANGUOYUN_WEBDAV_URL
                            remotePath = WebDavBackupConfiguration.DEFAULT_REMOTE_PATH
                            statusMessage = context.getString(R.string.cloud_backup_config_template_restored)
                            errorMessage = null
                        },
                        enabled = !isLoading && !isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.cloud_config_modern_status_title),
                description = stringResource(R.string.cloud_config_modern_status_waiting),
            ) {
                VaultSecondaryButton(
                    text = stringResource(R.string.cloud_config_modern_test_action),
                    onClick = {
                        statusMessage = context.getString(R.string.cloud_config_modern_status_waiting)
                        errorMessage = null
                    },
                    enabled = !isLoading && !isSaving,
                )
            }
        }
        item {
            VaultPrimaryButton(
                text = stringResource(
                    if (isSaving) {
                        R.string.cloud_config_modern_action_loading
                    } else {
                        R.string.cloud_config_modern_action
                    },
                ),
                onClick = {
                    val candidate = WebDavBackupConfiguration(
                        serverUrl = serviceUrl,
                        username = username,
                        appPassword = appPassword,
                        remotePath = remotePath,
                    ).normalized()
                    when {
                        candidate.serverUrl.isBlank() -> {
                            errorMessage = context.getString(R.string.cloud_backup_config_validation_service_url_blank)
                            statusMessage = null
                        }

                        !candidate.serverUrl.startsWith("https://") -> {
                            errorMessage = context.getString(R.string.cloud_backup_config_validation_service_url_https)
                            statusMessage = null
                        }

                        candidate.username.isBlank() -> {
                            errorMessage = context.getString(R.string.cloud_backup_config_validation_username_blank)
                            statusMessage = null
                        }

                        candidate.appPassword.isBlank() -> {
                            errorMessage = context.getString(R.string.cloud_backup_config_validation_app_password_blank)
                            statusMessage = null
                        }

                        else -> {
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                statusMessage = null
                                try {
                                    cloudBackupRepository.saveConfiguration(candidate)
                                    statusMessage = context.getString(R.string.cloud_backup_config_saved)
                                    onConfigurationSaved()
                                } catch (error: Exception) {
                                    errorMessage = error.message ?: context.getString(R.string.cloud_backup_config_save_failed)
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading && !isSaving,
            )
        }
    }
}
