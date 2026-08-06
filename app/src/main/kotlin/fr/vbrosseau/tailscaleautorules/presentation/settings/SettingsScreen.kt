package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/** Écran des paramètres, sans état. */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onServiceEnabledChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Une automatisation désactivée rend tous les autres réglages sans
        // effet : le dire vaut mieux que de laisser l'utilisateur chercher.
        if (!uiState.settings.isServiceEnabled) {
            NoticeCard(
                message = stringResource(R.string.settings_disabled_notice),
                testTag = SettingsTestTags.DISABLED_NOTICE,
            )
        }

        SwitchRow(
            title = stringResource(R.string.settings_service_title),
            summary = stringResource(R.string.settings_service_summary),
            checked = uiState.settings.isServiceEnabled,
            onCheckedChange = onServiceEnabledChange,
            testTag = SettingsTestTags.SERVICE,
        )

        SwitchRow(
            title = stringResource(R.string.settings_boot_title),
            summary = stringResource(R.string.settings_boot_summary),
            checked = uiState.settings.startOnBoot,
            onCheckedChange = onStartOnBootChange,
            testTag = SettingsTestTags.START_ON_BOOT,
        )

        NotificationSection(
            uiState = uiState,
            onPersistentNotificationChange = onPersistentNotificationChange,
            onRequestNotificationPermission = onRequestNotificationPermission,
        )

        SwitchRow(
            title = stringResource(R.string.settings_logging_title),
            summary = stringResource(R.string.settings_logging_summary),
            checked = uiState.settings.verboseLogging,
            onCheckedChange = onVerboseLoggingChange,
            testTag = SettingsTestTags.LOGGING,
        )

        if (!uiState.isIgnoringBatteryOptimizations) {
            ActionCard(
                message = stringResource(R.string.settings_battery_summary),
                actionLabel = stringResource(R.string.settings_battery_action),
                onAction = onOpenBatterySettings,
                testTag = SettingsTestTags.BATTERY,
            )
        }

        AboutCard(versionName = uiState.versionName)
    }
}

/**
 * Bascule de notification et demande de permission associée.
 *
 * Les deux sont regroupées parce qu'elles ne se comprennent qu'ensemble : la
 * permission n'est demandée qu'une fois l'option activée (SPECS.md §8), jamais
 * au premier lancement.
 */
@Composable
private fun NotificationSection(
    uiState: SettingsUiState,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SwitchRow(
            title = stringResource(R.string.settings_notification_title),
            summary = stringResource(R.string.settings_notification_summary),
            checked = uiState.settings.showPersistentNotification,
            onCheckedChange = onPersistentNotificationChange,
            testTag = SettingsTestTags.NOTIFICATION,
        )

        if (uiState.needsNotificationPermission) {
            ActionCard(
                message = stringResource(R.string.settings_notification_permission),
                actionLabel = stringResource(R.string.settings_notification_grant),
                onAction = onRequestNotificationPermission,
                testTag = SettingsTestTags.NOTIFICATION_PERMISSION,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}

@Composable
private fun NoticeCard(message: String, testTag: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(text = message, modifier = Modifier.padding(Spacing.md))
    }
}

@Composable
private fun ActionCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = message, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag("$testTag:action"),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AboutCard(versionName: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_version, versionName),
                modifier = Modifier.testTag(SettingsTestTags.VERSION),
            )
            Text(text = stringResource(R.string.settings_license))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AppTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                settings = AppSettings(showPersistentNotification = true),
                canNotify = false,
                isIgnoringBatteryOptimizations = false,
                versionName = "0.1.0",
            ),
            onServiceEnabledChange = {},
            onStartOnBootChange = {},
            onPersistentNotificationChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onOpenBatterySettings = {},
        )
    }
}
