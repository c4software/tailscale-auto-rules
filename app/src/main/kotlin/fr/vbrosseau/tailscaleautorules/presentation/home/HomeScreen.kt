package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.usecase.ManualOverride
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.labelRes
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/**
 * Écran d'accueil, sans état.
 *
 * Il ne reçoit qu'un [HomeUiState] et un rappel : aucune dérivation, aucun
 * accès à un ViewModel. C'est ce qui le rend prévisualisable et testable sans
 * injection.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSynchronize: () -> Unit,
    onDisableAutomation: () -> Unit,
    onChooseLearning: (Boolean) -> Unit,
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
        if (!uiState.isTailscaleInstalled) {
            MissingClientCard()
        }

        if (uiState.isLearningPromptVisible) {
            LearningPromptCard(onChooseLearning = onChooseLearning)
        }

        InfoCard(
            title = stringResource(R.string.home_tunnel_title),
            value = stringResource(uiState.tunnelState.labelRes()),
            valueTestTag = HomeTestTags.TUNNEL_STATE,
        )

        if (uiState.manualOverride != null) {
            ManualOverrideCard(
                manualOverride = uiState.manualOverride,
                willMemorize = uiState.willMemorizeManualGesture,
            )
        }

        InfoCard(
            title = stringResource(R.string.home_network_title),
            value = stringResource(uiState.transport.labelRes()),
            valueTestTag = HomeTestTags.NETWORK,
            // Le SSID n'a de sens qu'en Wi-Fi ; ailleurs, afficher
            // « indisponible » laisserait croire à un défaut de permission.
            secondary = when {
                uiState.transport != NetworkTransport.WIFI -> null
                uiState.ssid != null -> uiState.ssid
                else -> stringResource(R.string.home_ssid_unavailable)
            },
            secondaryTestTag = HomeTestTags.SSID,
        )

        InfoCard(
            title = stringResource(R.string.home_last_change_title),
            value = uiState.lastChange?.summary()
                ?: stringResource(R.string.home_no_change_yet),
            valueTestTag = HomeTestTags.LAST_CHANGE,
            secondary = uiState.lastChange?.let { stringResource(it.ruleId.labelRes()) },
        )

        SynchronizeButton(
            isSynchronizing = uiState.isSynchronizing,
            onSynchronize = onSynchronize,
        )

        AutomationSection(
            isAutomationEnabled = uiState.isAutomationEnabled,
            onDisableAutomation = onDisableAutomation,
        )
    }
}

/**
 * Signale un tunnel mis à la main dans l'état inverse de ce qu'une règle a
 * décidé — typiquement activé sur un réseau de confiance.
 *
 * Sans cette carte, l'écran juxtaposerait un état constaté et une règle qui
 * dit le contraire, en laissant l'utilisateur arbitrer. La carte assume le
 * constat, et son texte suit le sort réel du geste : mémorisé comme exception
 * (SPECS.md §3.3), ou simplement respecté jusqu'au prochain changement de
 * réseau quand l'apprentissage est coupé ou le réseau non identifiable.
 */
@Composable
private fun ManualOverrideCard(
    manualOverride: ManualOverride,
    willMemorize: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.MANUAL_OVERRIDE),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        val isEnabled = manualOverride.observedState == TunnelState.ENABLED
        val bodyRes = when {
            willMemorize && isEnabled -> R.string.home_manual_override_enabled_memorized
            willMemorize -> R.string.home_manual_override_disabled_memorized
            isEnabled -> R.string.home_manual_override_enabled
            else -> R.string.home_manual_override_disabled
        }

        Text(
            text = stringResource(bodyRes, stringResource(manualOverride.ruleId.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

/**
 * Invitation unique du premier lancement (SPECS.md §6.1).
 *
 * Les deux réponses sont des boutons de même rang : « ne pas activer » est un
 * choix légitime, pas un renoncement, et l'invitation ne revient jamais quelle
 * que soit la réponse.
 */
@Composable
private fun LearningPromptCard(
    onChooseLearning: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.LEARNING_PROMPT),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_learning_prompt_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.home_learning_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { onChooseLearning(false) },
                    modifier = Modifier.testTag(HomeTestTags.LEARNING_DECLINE),
                ) {
                    Text(stringResource(R.string.home_learning_prompt_decline))
                }
                // Un bouton plein, pas tonal : sur cette carte en
                // `secondaryContainer`, un `FilledTonalButton` — du même
                // conteneur — disparaissait en thème sombre.
                Button(
                    onClick = { onChooseLearning(true) },
                    modifier = Modifier.testTag(HomeTestTags.LEARNING_ACCEPT),
                ) {
                    Text(stringResource(R.string.home_learning_prompt_accept))
                }
            }
        }
    }
}

/**
 * Coupe l'automatisation, ou constate qu'elle l'est déjà.
 *
 * Le bouton ne disparaît pas sans laisser de trace : une carte prend sa place,
 * sans quoi l'utilisateur ne saurait ni que son geste a porté, ni pourquoi le
 * tunnel ne bouge plus tout seul.
 */
@Composable
private fun AutomationSection(
    isAutomationEnabled: Boolean,
    onDisableAutomation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isAutomationEnabled) {
        OutlinedButton(
            onClick = onDisableAutomation,
            modifier = modifier
                .fillMaxWidth()
                .testTag(HomeTestTags.DISABLE_AUTOMATION),
        ) {
            Text(stringResource(R.string.home_disable_automation))
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag(HomeTestTags.AUTOMATION_DISABLED),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.home_automation_disabled),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun SynchronizeButton(
    isSynchronizing: Boolean,
    onSynchronize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onSynchronize,
        enabled = !isSynchronizing,
        modifier = modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.SYNCHRONIZE),
    ) {
        if (isSynchronizing) {
            CircularProgressIndicator(
                modifier = Modifier.size(Spacing.md),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.home_synchronizing),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        } else {
            Text(stringResource(R.string.home_synchronize))
        }
    }
}

@Composable
private fun JournalEntry.summary(): String = stringResource(
    R.string.home_change_summary,
    stringResource(previousState.labelRes()),
    stringResource(newState.labelRes()),
)

@Composable
private fun InfoCard(
    title: String,
    value: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    secondaryTestTag: String? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(valueTestTag),
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = secondaryTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
    }
}

@Composable
private fun MissingClientCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.TAILSCALE_MISSING),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.home_tailscale_missing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.home_tailscale_missing_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AppTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(
                tunnelState = TunnelState.ENABLED,
                transport = NetworkTransport.WIFI,
                ssid = "Aéroport CDG",
                lastChange = JournalEntry(
                    id = 1,
                    epochMillis = 0,
                    previousState = TunnelState.DISABLED,
                    newState = TunnelState.ENABLED,
                    ruleId = RuleId("other-wifi"),
                ),
            ),
            onSynchronize = {},
            onDisableAutomation = {},
            onChooseLearning = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenWithoutClientPreview() {
    AppTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(isTailscaleInstalled = false),
            onSynchronize = {},
            onDisableAutomation = {},
            onChooseLearning = {},
        )
    }
}
