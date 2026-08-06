package fr.vbrosseau.tailscaleautorules.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.presentation.labelRes
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing
import java.time.ZoneId
import java.util.Locale

/**
 * Écran du journal, sans état applicatif.
 *
 * Le fuseau et la langue sont des paramètres : les tests s'affranchissent ainsi
 * des réglages de la machine qui les exécute, ce qui rend l'affichage des dates
 * vérifiable.
 */
@Composable
fun JournalScreen(
    uiState: JournalUiState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    var confirmingClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.journal_explanation, JournalRepository.MAX_ENTRIES),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (uiState.isEmpty) {
            Text(
                text = stringResource(R.string.journal_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(JournalTestTags.EMPTY),
            )
        } else {
            // L'effacement n'est proposé que s'il y a quelque chose à effacer.
            OutlinedButton(
                onClick = { confirmingClear = true },
                modifier = Modifier.testTag(JournalTestTags.CLEAR),
            ) {
                Text(stringResource(R.string.journal_clear))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.testTag(JournalTestTags.LIST),
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    EntryCard(entry = entry, zoneId = zoneId, locale = locale)
                }
            }
        }
    }

    if (confirmingClear) {
        // L'effacement est irréversible : il se confirme.
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.journal_clear_confirm_title)) },
            text = { Text(stringResource(R.string.journal_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        confirmingClear = false
                    },
                    modifier = Modifier.testTag(JournalTestTags.CLEAR_CONFIRM),
                ) {
                    Text(stringResource(R.string.journal_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.journal_cancel))
                }
            },
        )
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntry,
    zoneId: ZoneId,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(
                        R.string.journal_transition,
                        stringResource(entry.previousState.labelRes()),
                        stringResource(entry.newState.labelRes()),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(JournalTestTags.transition(entry.id)),
                )
                Text(
                    text = formatJournalTimestamp(entry.epochMillis, zoneId, locale),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag(JournalTestTags.timestamp(entry.id)),
                )
            }
            Text(
                text = stringResource(entry.ruleId.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(JournalTestTags.rule(entry.id)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalScreenPreview() {
    AppTheme(dynamicColor = false) {
        JournalScreen(
            uiState = JournalUiState(
                entries = listOf(
                    JournalEntry(
                        id = 2,
                        epochMillis = 1_770_000_000_000,
                        previousState = TunnelState.ENABLED,
                        newState = TunnelState.DISABLED,
                        ruleId = RuleId("blacklisted-wifi"),
                    ),
                    JournalEntry(
                        id = 1,
                        epochMillis = 1_769_990_000_000,
                        previousState = TunnelState.DISABLED,
                        newState = TunnelState.ENABLED,
                        ruleId = RuleId("mobile-network"),
                    ),
                ),
            ),
            onClear = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        JournalScreen(uiState = JournalUiState(), onClear = {})
    }
}
