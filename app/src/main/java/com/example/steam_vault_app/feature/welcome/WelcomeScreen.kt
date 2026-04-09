package com.example.steam_vault_app.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.steam_vault_app.R
import com.example.steam_vault_app.ui.common.VaultInfoPill
import com.example.steam_vault_app.ui.common.VaultPrimaryButton
import com.example.steam_vault_app.ui.common.VaultSecondaryButton

@Composable
fun WelcomeScreen(
    onNavigateToCreatePassword: () -> Unit,
    onNavigateToRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WelcomeMonolithHero()
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_modern_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_modern_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VaultInfoPill(text = stringResource(R.string.vault_status_local_only))
                VaultInfoPill(text = stringResource(R.string.vault_status_backup_ready))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VaultPrimaryButton(
                text = stringResource(R.string.welcome_modern_primary),
                onClick = onNavigateToCreatePassword,
                leadingIcon = Icons.AutoMirrored.Filled.ArrowForward,
            )
            VaultSecondaryButton(
                text = stringResource(R.string.welcome_modern_secondary),
                onClick = onNavigateToRestoreBackup,
            )
            Text(
                text = stringResource(R.string.welcome_modern_note_secondary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun WelcomeMonolithHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.76f)
            .aspectRatio(0.9f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 190.dp)
                .rotate(-14f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.36f),
                    shape = RoundedCornerShape(40.dp),
                )
                .align(Alignment.CenterStart),
        )
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 184.dp)
                .rotate(-8f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(40.dp),
                )
                .align(Alignment.Center),
        )
        Surface(
            modifier = Modifier
                .size(width = 86.dp, height = 192.dp)
                .align(Alignment.CenterEnd),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 18.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.TopEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
