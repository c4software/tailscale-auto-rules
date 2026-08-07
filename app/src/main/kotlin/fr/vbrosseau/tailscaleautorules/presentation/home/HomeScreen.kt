package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        InfoCard(
            title = stringResource(R.string.home_tunnel_title),
            value = stringResource(uiState.tunnelState.labelRes()),
            valueTestTag = HomeTestTags.TUNNEL_STATE,
        )

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
        )
    }
}
