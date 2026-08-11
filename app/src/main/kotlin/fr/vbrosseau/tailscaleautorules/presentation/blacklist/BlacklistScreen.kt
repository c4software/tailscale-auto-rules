package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkException
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.SwitchCard
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
    onRemoveException: (Long) -> Unit,
    onAddCurrentSsid: () -> Unit,
    onDismissError: () -> Unit,
    onMobileRuleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRequestLocationPermission: () -> Unit = {},
) {
    if (uiState.isLoading) {
        LoadingIndicator(modifier = modifier)
        return
    }

    var editing by remember { mutableStateOf<EditingState?>(null) }

    // Une seule liste défilante pour tout l'écran : avec la carte de la règle
    // mobile, les cartes d'explication et la liste, le contenu déborde d'un
    // petit écran — un en-tête figé rendrait le bas inatteignable.
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BlacklistTestTags.LIST),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        headerItems(
            uiState = uiState,
            onMobileRuleChange = onMobileRuleChange,
            onRequestLocationPermission = onRequestLocationPermission,
            onDismissError = onDismissError,
        )

        item {
            ActionRow(
                uiState = uiState,
                onStartCreation = { editing = EditingState(id = null, value = "") },
                onAddCurrentSsid = onAddCurrentSsid,
            )
        }

        entryItems(
            entries = uiState.entries,
            onStartRename = { entry -> editing = EditingState(entry.id, entry.value) },
            onRemove = onRemove,
        )

        exceptionItems(
            exceptions = uiState.exceptions,
            onRemoveException = onRemoveException,
        )
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

/** Cartes de tête : règle du réseau mobile, explication, permission, erreur. */
private fun LazyListScope.headerItems(
    uiState: BlacklistUiState,
    onMobileRuleChange: (Boolean) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onDismissError: () -> Unit,
) {
    item {
        // La règle du réseau mobile vit sur cet écran plutôt qu'aux
        // paramètres : c'est ici que l'utilisateur décide sur quels réseaux
        // le tunnel monte.
        SwitchCard(
            title = stringResource(R.string.blacklist_mobile_title),
            summary = stringResource(R.string.blacklist_mobile_summary),
            checked = uiState.isMobileRuleEnabled,
            onCheckedChange = onMobileRuleChange,
            testTag = BlacklistTestTags.MOBILE_RULE,
        )
    }

    item {
        Text(
            text = stringResource(R.string.blacklist_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // L'explication précède la demande, comme l'exige le Play Store, et
    // n'apparaît que sur cet écran : c'est ici que l'utilisateur découvre que
    // ses réseaux de confiance ne pourront pas être reconnus.
    if (uiState.needsLocationPermission) {
        item { LocationRationaleCard(onGrant = onRequestLocationPermission) }
    }

    uiState.error?.let { error ->
        item {
            ErrorCard(message = stringResource(error.labelRes()), onDismiss = onDismissError)
        }
    }
}

/** Les réseaux enregistrés — ou l'explication de leur absence. */
private fun LazyListScope.entryItems(
    entries: List<BlacklistedSsid>,
    onStartRename: (BlacklistedSsid) -> Unit,
    onRemove: (Long) -> Unit,
) {
    if (entries.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.blacklist_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(BlacklistTestTags.EMPTY),
            )
        }
    } else {
        items(entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                onRename = { onStartRename(entry) },
                onRemove = { onRemove(entry.id) },
            )
        }
    }
}

/**
 * Les gestes mémorisés (SPECS.md §6.2) — la section entière disparaît quand il
 * n'y a rien à montrer : un titre orphelin poserait une question sans réponse.
 */
private fun LazyListScope.exceptionItems(
    exceptions: List<NetworkException>,
    onRemoveException: (Long) -> Unit,
) {
    if (exceptions.isEmpty()) return

    item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.blacklist_exceptions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(BlacklistTestTags.EXCEPTIONS_TITLE),
            )
            Text(
                text = stringResource(R.string.blacklist_exceptions_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    items(exceptions, key = { "exception-${it.id}" }) { exception ->
        ExceptionRow(
            exception = exception,
            onRemove = { onRemoveException(exception.id) },
        )
    }
}

/**
 * Un geste mémorisé, supprimable d'un glissement latéral.
 *
 * La suppression est déclenchée à l'**aboutissement** du glissement — pas dans
 * `confirmValueChange`, que la mécanique de geste peut consulter plusieurs
 * fois pour une même sortie. La carte disparaît par la liste observée, jamais
 * par un état visuel local qui pourrait la masquer sans rien effacer.
 */
@Composable
private fun ExceptionRow(
    exception: NetworkException,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) currentOnRemove()
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag(BlacklistTestTags.exception(exception.id)),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(
                        R.string.blacklist_remove,
                        exception.ssid ?: stringResource(R.string.blacklist_exception_cellular),
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = exception.ssid
                        ?: stringResource(R.string.blacklist_exception_cellular),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        if (exception.desiredState == TunnelState.ENABLED) {
                            R.string.blacklist_exception_enabled
                        } else {
                            R.string.blacklist_exception_disabled
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
                exceptions = listOf(
                    NetworkException(
                        id = 1,
                        key = NetworkExceptionKey("wifi:maison"),
                        ssid = "Maison",
                        desiredState = TunnelState.ENABLED,
                        epochMillis = 0,
                    ),
                    NetworkException(
                        id = 2,
                        key = NetworkExceptionKey.Cellular,
                        ssid = null,
                        desiredState = TunnelState.DISABLED,
                        epochMillis = 0,
                    ),
                ),
                currentSsid = "Aéroport CDG",
            ),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onRemoveException = {},
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
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
            onRemoveException = {},
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }
}
