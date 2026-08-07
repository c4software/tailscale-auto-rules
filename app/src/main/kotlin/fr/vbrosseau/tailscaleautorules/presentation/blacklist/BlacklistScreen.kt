package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/**
 * Écran des réseaux de confiance, sans état applicatif.
 *
 * Le seul état local est celui de la boîte de dialogue de saisie : il n'a
 * aucune raison de survivre à l'écran, et le confier au ViewModel l'obligerait
 * à connaître une mécanique purement visuelle.
 */
@Composable
fun BlacklistScreen(
    uiState: BlacklistUiState,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onAddCurrentSsid: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestLocationPermission: () -> Unit = {},
) {
    if (uiState.isLoading) {
        LoadingIndicator(modifier = modifier)
        return
    }

    var editing by remember { mutableStateOf<EditingState?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.blacklist_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )

        // L'explication précède la demande, comme l'exige le Play Store, et
        // n'apparaît que sur cet écran : c'est ici que l'utilisateur découvre
        // que ses réseaux de confiance ne pourront pas être reconnus.
        if (uiState.needsLocationPermission) {
            LocationRationaleCard(onGrant = onRequestLocationPermission)
        }

        uiState.error?.let { error ->
            ErrorCard(message = stringResource(error.labelRes()), onDismiss = onDismissError)
        }

        ActionRow(
            uiState = uiState,
            onStartCreation = { editing = EditingState(id = null, value = "") },
            onAddCurrentSsid = onAddCurrentSsid,
        )

        if (uiState.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.blacklist_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(BlacklistTestTags.EMPTY),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.testTag(BlacklistTestTags.LIST),
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onRename = { editing = EditingState(entry.id, entry.value) },
                        onRemove = { onRemove(entry.id) },
                    )
                }
            }
        }
    }

    editing?.let { state ->
        SsidDialog(
            initialValue = state.value,
            onDismiss = { editing = null },
            onConfirm = { value ->
                if (state.id == null) onAdd(value) else onRename(state.id, value)
                editing = null
            },
        )
    }
}

@Composable
private fun ActionRow(
    uiState: BlacklistUiState,
    onStartCreation: () -> Unit,
    onAddCurrentSsid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = onStartCreation,
            modifier = Modifier.testTag(BlacklistTestTags.ADD),
        ) {
            Text(stringResource(R.string.blacklist_add))
        }

        // Le bouton n'apparaît que s'il peut aboutir : proposer un ajout voué
        // à échouer serait une invitation à l'erreur.
        if (uiState.canAddCurrentSsid) {
            OutlinedButton(
                onClick = onAddCurrentSsid,
                modifier = Modifier.testTag(BlacklistTestTags.ADD_CURRENT),
            ) {
                Text(
                    stringResource(
                        R.string.blacklist_add_current,
                        uiState.currentSsid.orEmpty(),
                    ),
                )
            }
        }
    }
}

/**
 * Explique pourquoi la localisation est demandée, **avant** de la demander.
 *
 * Le texte dit aussi ce que l'application ne fait pas : Android impose cette
 * permission pour lire un SSID, ce que rien n'indique à l'utilisateur, et une
 * demande non expliquée serait à juste titre refusée — par lui comme par le
 * Play Store.
 */
@Composable
private fun LocationRationaleCard(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BlacklistTestTags.LOCATION_RATIONALE),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            // Sans `fillMaxWidth`, la colonne s'ajuste au texte et l'alignement
            // à droite du bouton se fait dans ce cadre rétréci, pas dans la carte.
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.blacklist_location_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.blacklist_location_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Un bouton plein plutôt qu'un bouton texte : aligné à gauche et
            // sans contour, l'action se lisait comme un paragraphe de plus.
            FilledTonalButton(
                onClick = onGrant,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(BlacklistTestTags.LOCATION_GRANT),
            ) {
                Text(stringResource(R.string.blacklist_location_grant))
            }
        }
    }
}

/** Édition en cours : [id] à `null` pour une création. */
private data class EditingState(val id: Long?, val value: String)

@Composable
private fun EntryRow(
    entry: BlacklistedSsid,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = entry.value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BlacklistTestTags.entry(entry.id)),
            )
            TextButton(
                onClick = onRename,
                modifier = Modifier.testTag(BlacklistTestTags.rename(entry.id)),
            ) {
                Text(stringResource(R.string.blacklist_rename))
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.testTag(BlacklistTestTags.remove(entry.id)),
            ) {
                Text(stringResource(R.string.blacklist_remove, entry.value))
            }
        }
    }
}

@Composable
private fun SsidDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blacklist_ssid_label)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.testTag(BlacklistTestTags.DIALOG_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                modifier = Modifier.testTag(BlacklistTestTags.DIALOG_CONFIRM),
            ) {
                Text(stringResource(R.string.blacklist_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.blacklist_cancel))
            }
        },
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BlacklistTestTags.ERROR),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            // Sans `fillMaxWidth`, la colonne s'ajuste au texte et l'alignement
            // à droite du bouton se fait dans ce cadre rétréci, pas dans la carte.
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = message)
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.blacklist_cancel))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BlacklistScreenPreview() {
    AppTheme(dynamicColor = false) {
        BlacklistScreen(
            uiState = BlacklistUiState(
                entries = listOf(
                    BlacklistedSsid(id = 1, value = "Maison"),
                    BlacklistedSsid(id = 2, value = "Bureau"),
                ),
                currentSsid = "Aéroport CDG",
            ),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BlacklistScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        BlacklistScreen(
            uiState = BlacklistUiState(error = BlacklistError.DUPLICATE),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
        )
    }
}
