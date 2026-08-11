package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.SwitchCard
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/** Écran des paramètres, sans état. */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onServiceEnabledChange: (Boolean) -> Unit,
    onLearningEnabledChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        LoadingIndicator(modifier = modifier)
        return
    }

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

        MainSwitches(
            settings = uiState.settings,
            onServiceEnabledChange = onServiceEnabledChange,
            onLearningEnabledChange = onLearningEnabledChange,
            onStartOnBootChange = onStartOnBootChange,
        )

        NotificationSection(
            uiState = uiState,
            onRequestNotificationPermission = onRequestNotificationPermission,
        )

        SwitchCard(
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

/** Les trois bascules de tête, sorties de l'écran pour le garder d'un seul tenant. */
@Composable
private fun MainSwitches(
    settings: AppSettings,
    onServiceEnabledChange: (Boolean) -> Unit,
    onLearningEnabledChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SwitchCard(
            title = stringResource(R.string.settings_service_title),
            summary = stringResource(R.string.settings_service_summary),
            checked = settings.isServiceEnabled,
            onCheckedChange = onServiceEnabledChange,
            testTag = SettingsTestTags.SERVICE,
        )

        SwitchCard(
            title = stringResource(R.string.settings_learning_title),
            summary = stringResource(R.string.settings_learning_summary),
            checked = settings.isLearningEnabled,
            onCheckedChange = onLearningEnabledChange,
            testTag = SettingsTestTags.LEARNING,
        )

        SwitchCard(
            title = stringResource(R.string.settings_boot_title),
            summary = stringResource(R.string.settings_boot_summary),
            checked = settings.startOnBoot,
            onCheckedChange = onStartOnBootChange,
            testTag = SettingsTestTags.START_ON_BOOT,
        )
    }
}

/**
 * Explication de la notification permanente, et demande de permission associée.
 *
 * **Ce n'est délibérément pas une bascule.** La notification est imposée par
 * Android au service qui observe le réseau : offrir un interrupteur — fût-il
 * grisé — laisserait croire à un choix qui n'existe pas. Un interrupteur coché
 * et désactivé se lit d'ailleurs mal, son rendu étant presque celui d'un
 * interrupteur éteint.
 */
@Composable
private fun NotificationSection(
    uiState: SettingsUiState,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (uiState.settings.notificationIsVisible) {
            NoticeCard(
                message = stringResource(R.string.settings_notification_notice),
                testTag = SettingsTestTags.NOTIFICATION,
            )
        }

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
        // Le bouton est placé sous le message, et non à côté : côte à côte, il
        // lui prenait la largeur et hachait le texte en lignes très courtes.
        // `fillMaxWidth` est indispensable — sans lui la colonne s'ajuste au
        // texte, et l'alignement à droite du bouton se fait dans ce cadre
        // rétréci plutôt que dans la carte.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = message)
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("$testTag:action"),
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
                settings = AppSettings(),
                canNotify = false,
                isIgnoringBatteryOptimizations = false,
                versionName = "0.1.0",
            ),
            onServiceEnabledChange = {},
            onLearningEnabledChange = {},
            onStartOnBootChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onOpenBatterySettings = {},
        )
    }
}
