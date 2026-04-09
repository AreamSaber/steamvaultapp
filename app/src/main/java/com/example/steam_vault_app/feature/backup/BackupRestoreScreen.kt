package com.example.steam_vault_app.feature.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.steam_vault_app.ui.common.VaultBannerTone
import com.example.steam_vault_app.ui.common.VaultInlineBanner
import com.example.steam_vault_app.ui.common.VaultPageHeader
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreen(
    vaultRepository: VaultRepository,
    onRestoreCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        scope.launch {
            if (uri == null) {
                statusMessage = context.getString(R.string.backup_restore_cancelled_before_pick)
                errorMessage = null
                isWorking = false
                return@launch
            }

            try {
                val rawPayload = readBackupPayload(context, uri)
                val backupPackage = LocalBackupPayloadCodec.decode(rawPayload)
                vaultRepository.restoreLocalBackup(backupPackage)
                statusMessage = context.getString(R.string.backup_restore_success)
                errorMessage = null
                onRestoreCompleted()
            } catch (error: IllegalArgumentException) {
                errorMessage = error.message ?: context.getString(R.string.backup_restore_invalid_file)
                statusMessage = null
            } catch (error: Exception) {
                errorMessage = error.message ?: context.getString(R.string.backup_restore_failed)
                statusMessage = null
            } finally {
                isWorking = false
            }
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
                title = stringResource(R.string.backup_restore_modern_title),
                subtitle = stringResource(R.string.backup_restore_modern_body),
            )
        }
        statusMessage?.let { message ->
            item {
                VaultInlineBanner(
                    text = message,
                    tone = VaultBannerTone.Success,
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
                title = stringResource(R.string.backup_restore_modern_card_title),
                description = stringResource(R.string.backup_restore_modern_card_body),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_modern_caution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VaultPrimaryButton(
                    text = stringResource(
                        if (isWorking) {
                            R.string.backup_restore_modern_action_loading
                        } else {
                            R.string.backup_restore_modern_action
                        },
                    ),
                    onClick = {
                        isWorking = true
                        statusMessage = null
                        errorMessage = null
                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    enabled = !isWorking,
                )
            }
        }
    }
}

private fun readBackupPayload(
    context: Context,
    uri: Uri,
): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException(context.getString(R.string.backup_restore_input_unavailable))
    return inputStream.use { stream ->
        stream.readBytes().toString(StandardCharsets.UTF_8)
    }
}
