import re

file_path = "app/src/main/java/com/example/steam_vault_app/feature/tokens/TokenListScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace the imports to include what we need
if "import androidx.compose.foundation.shape.RoundedCornerShape" not in content:
    content = content.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.ContentCopy\nimport androidx.compose.material3.Icon")

# We want to replace the VaultTokenCard Composable
new_vault_token_card = """@Composable
private fun VaultTokenCard(
    token: TokenRecord,
    currentEpochSeconds: Long,
    vaultCryptography: VaultCryptography,
    onOpenTokenDetails: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val copyActionText = stringResource(R.string.token_list_modern_copy)
    val snapshot = buildSteamCodeSnapshot(
        token = token,
        currentEpochSeconds = currentEpochSeconds,
        vaultCryptography = vaultCryptography,
        errorCodeDisplay = stringResource(R.string.token_code_error_display),
    )

    val isWarning = snapshot.secondsRemaining <= 5
    val primaryColor = if (isWarning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTokenDetails(token.id) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.token_list_modern_account_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = token.accountName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                
                // Progress Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { snapshot.progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 3.dp,
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(
                        text = "${snapshot.secondsRemaining}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                    )
                }
            }
            
            // Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = snapshot.codeDisplay,
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isWarning) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.clickable(enabled = !snapshot.hasSecretError) {
                        clipboardManager.setText(AnnotatedString(snapshot.codeDisplay))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = copyActionText,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = copyActionText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            
            if (snapshot.hasSecretError) {
                VaultInlineBanner(
                    text = stringResource(R.string.token_card_secret_error),
                    tone = VaultBannerTone.Error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
"""

content = re.sub(r'@Composable\s*private fun VaultTokenCard.*$', new_vault_token_card, content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)

