package com.example.steam_vault_app.feature.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.data.backup.LocalBackupPayloadCodec
import com.example.steam_vault_app.domain.repository.VaultRepository
import com.example.steam_vault_app.ui.common.ScreenSectionCard
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BackupExportScreen(
    vaultRepository: VaultRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPayload by remember { mutableStateOf<String?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        scope.launch {
            if (uri == null) {
                pendingPayload = null
                pendingFileName = null
                statusMessage = context.getString(R.string.backup_export_cancelled_before_create)
                errorMessage = null
                isWorking = false
                return@launch
            }

            val payload = pendingPayload
            if (payload == null) {
                errorMessage = context.getString(R.string.backup_export_payload_unavailable)
                statusMessage = null
                isWorking = false
                return@launch
            }

            try {
                writeBackupPayload(
                    context = context,
                    uri = uri,
                    payload = payload,
                )
                statusMessage = context.getString(
                    R.string.backup_export_saved,
                    pendingFileName ?: context.getString(R.string.backup_export_selected_file_fallback),
                )
                errorMessage = null
            } catch (_: Exception) {
                errorMessage = context.getString(R.string.backup_export_write_failed)
                statusMessage = null
            } finally {
                pendingPayload = null
                pendingFileName = null
                isWorking = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.backup_export_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.backup_export_section_title),
                description = stringResource(R.string.backup_export_section_description),
            ) {
                Text(
                    text = stringResource(R.string.backup_export_warning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            statusMessage = null
                            errorMessage = null

                            try {
                                val backupPackage = vaultRepository.exportLocalBackup()
                                val payload = LocalBackupPayloadCodec.encode(backupPackage)
                                val suggestedFileName = buildBackupFileName(backupPackage.exportedAt)
                                pendingPayload = payload
                                pendingFileName = suggestedFileName
                                createDocumentLauncher.launch(suggestedFileName)
                            } catch (_: Exception) {
                                errorMessage = context.getString(R.string.backup_export_generate_failed)
                                statusMessage = null
                                pendingPayload = null
                                pendingFileName = null
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isWorking) {
                                R.string.backup_export_action_loading
                            } else {
                                R.string.backup_export_action_idle
                            },
                        ),
                    )
                }
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
        errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun writeBackupPayload(
    context: Context,
    uri: Uri,
    payload: String,
) {
    val outputStream = context.contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException(context.getString(R.string.backup_export_output_unavailable))
    outputStream.use { stream ->
        stream.write(payload.toByteArray(StandardCharsets.UTF_8))
    }
}

private fun buildBackupFileName(exportedAt: String): String {
    val parsedDate = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(exportedAt)
    }.getOrNull() ?: Date()

    val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(parsedDate)
    return "steam-vault-backup-$fileStamp.json"
}
