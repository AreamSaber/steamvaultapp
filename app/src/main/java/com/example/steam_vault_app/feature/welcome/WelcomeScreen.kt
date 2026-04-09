package com.example.steam_vault_app.feature.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.ChecklistRow
import com.example.steam_vault_app.ui.common.ScreenSectionCard

@Composable
fun WelcomeScreen(
    onCreatePassword: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlights = listOf(
        stringResource(R.string.welcome_highlight_codes),
        stringResource(R.string.welcome_highlight_local_storage),
        stringResource(R.string.welcome_highlight_sensitive_protection),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.welcome_headline),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.welcome_focus_title),
                description = stringResource(R.string.welcome_focus_description),
            ) {
                highlights.forEachIndexed { index, item ->
                    ChecklistRow(
                        label = item,
                        highlighted = index == 0,
                    )
                }
            }
        }
        item {
            ScreenSectionCard(
                title = stringResource(R.string.welcome_flow_title),
                description = stringResource(R.string.welcome_flow_description),
            ) {
                ChecklistRow(label = stringResource(R.string.welcome_flow_create))
                ChecklistRow(label = stringResource(R.string.welcome_flow_restore))
                ChecklistRow(label = stringResource(R.string.welcome_flow_manage))
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCreatePassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_action_create_password))
                }
                OutlinedButton(
                    onClick = onRestoreBackup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_action_restore_backup))
                }
            }
        }
    }
}
