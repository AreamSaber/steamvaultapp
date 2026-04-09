package com.example.steam_vault_app.feature.cloudbackup

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.cloud_backup_config_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.cloud_backup_config_section_title),
                description = stringResource(R.string.cloud_backup_config_section_description),
            ) {
                Text(
                    text = stringResource(
                        R.string.cloud_backup_config_jianguoyun_url,
                        DEFAULT_JIANGUOYUN_WEBDAV_URL,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.cloud_backup_config_use_app_password),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        statusMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = serviceUrl,
                    onValueChange = { serviceUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_backup_config_label_service_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    enabled = !isLoading && !isSaving,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_backup_config_label_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    enabled = !isLoading && !isSaving,
                )
                OutlinedTextField(
                    value = appPassword,
                    onValueChange = { appPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_backup_config_label_app_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    enabled = !isLoading && !isSaving,
                )
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_backup_config_label_remote_path)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    enabled = !isLoading && !isSaving,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
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
                                        statusMessage = null
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoading && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSaving) {
                                R.string.cloud_backup_config_action_loading
                            } else {
                                R.string.cloud_backup_config_action_idle
                            },
                        ),
                    )
                }
                OutlinedButton(
                    onClick = {
                        serviceUrl = DEFAULT_JIANGUOYUN_WEBDAV_URL
                        remotePath = WebDavBackupConfiguration.DEFAULT_REMOTE_PATH
                        statusMessage = context.getString(R.string.cloud_backup_config_template_restored)
                        errorMessage = null
                    },
                    enabled = !isLoading && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cloud_backup_config_action_restore_template))
                }
            }
        }
    }
}
