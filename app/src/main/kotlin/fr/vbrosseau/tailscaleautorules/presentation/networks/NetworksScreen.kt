package fr.vbrosseau.tailscaleautorules.presentation.networks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
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
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.SwitchCard
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/**
 * Écran des réseaux, sans état applicatif.
 *
 * Une seule liste : les préférences de réseau (SPECS.md §4.2 et §6.2),
 * déclarées ou apprises d'un geste. Le seul état local est celui de la boîte
 * de dialogue de saisie : il n'a aucune raison de survivre à l'écran.
 */
@Composable
fun NetworksScreen(
    uiState: NetworksUiState,
    onAdd: (String, Boolean) -> Unit,
    onRename: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onSetPreferenceEnabled: (NetworkPreference, Boolean) -> Unit,
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
            .testTag(NetworksTestTags.LIST),
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

        preferenceItems(
            preferences = uiState.preferences,
            onStartRename = { preference ->
                editing = EditingState(preference.id, preference.ssid.orEmpty())
            },
            onRemove = onRemove,
            onSetPreferenceEnabled = onSetPreferenceEnabled,
        )
    }

    editing?.let { state ->
        PreferenceDialog(
            initialValue = state.value,
            withBehaviourChoice = state.id == null,
            onDismiss = { editing = null },
            onConfirm = { value, tunnelEnabled ->
                if (state.id == null) onAdd(value, tunnelEnabled) else onRename(state.id, value)
                editing = null
            },
        )
    }
}

/** Cartes de tête : règle du réseau mobile, explication, permission, erreur. */
private fun LazyListScope.headerItems(
    uiState: NetworksUiState,
    onMobileRuleChange: (Boolean) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onDismissError: () -> Unit,
) {
    item {
        // La règle du réseau mobile vit sur cet écran plutôt qu'aux
        // paramètres : c'est ici que l'utilisateur décide sur quels réseaux
        // le tunnel monte.
        SwitchCard(
            title = stringResource(R.string.networks_mobile_title),
            summary = stringResource(R.string.networks_mobile_summary),
            checked = uiState.isMobileRuleEnabled,
            onCheckedChange = onMobileRuleChange,
            testTag = NetworksTestTags.MOBILE_RULE,
        )
    }

    item {
        Text(
            text = stringResource(R.string.networks_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // L'explication précède la demande, comme l'exige le Play Store, et
    // n'apparaît que sur cet écran : c'est ici que l'utilisateur découvre que
    // ses réseaux ne pourront pas être reconnus.
    if (uiState.needsLocationPermission) {
        item { LocationRationaleCard(onGrant = onRequestLocationPermission) }
    }

    uiState.error?.let { error ->
        item {
            ErrorCard(message = stringResource(error.labelRes()), onDismiss = onDismissError)
        }
    }
}

/** Les préférences enregistrées — ou l'explication de leur absence. */
private fun LazyListScope.preferenceItems(
    preferences: List<NetworkPreference>,
    onStartRename: (NetworkPreference) -> Unit,
    onRemove: (Long) -> Unit,
    onSetPreferenceEnabled: (NetworkPreference, Boolean) -> Unit,
) {
    if (preferences.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.networks_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(NetworksTestTags.EMPTY),
            )
        }
    } else {
        items(preferences, key = { it.id }) { preference ->
            PreferenceRow(
                preference = preference,
                onRename = { onStartRename(preference) },
                onRemove = { onRemove(preference.id) },
                onSetEnabled = { enabled -> onSetPreferenceEnabled(preference, enabled) },
            )
        }
    }
}

@Composable
private fun ActionRow(
    uiState: NetworksUiState,
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
            modifier = Modifier.testTag(NetworksTestTags.ADD),
        ) {
            Text(stringResource(R.string.networks_add))
        }

        // Le bouton n'apparaît que s'il peut aboutir : proposer un ajout voué
        // à échouer serait une invitation à l'erreur.
        if (uiState.canAddCurrentSsid) {
            OutlinedButton(
                onClick = onAddCurrentSsid,
                modifier = Modifier.testTag(NetworksTestTags.ADD_CURRENT),
            ) {
                Text(
                    stringResource(
                        R.string.networks_add_current,
                        uiState.currentSsid.orEmpty(),
                    ),
                )
            }
        }
    }
}

/**
 * Une préférence : réseau, volonté, et les gestes pour en changer.
 *
 * L'interrupteur porte la volonté — tunnel actif ou coupé —, le nom se
 * renomme d'un appui (Wi-Fi seulement : les données mobiles n'ont pas de
 * nom), et un glissement latéral rend le réseau à l'automatisme. La
 * suppression est déclenchée à l'**aboutissement** du glissement — pas dans
 * `confirmValueChange`, que la mécanique de geste peut consulter plusieurs
 * fois pour une même sortie.
 */
@Composable
private fun PreferenceRow(
    preference: NetworkPreference,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) currentOnRemove()
    }

    val name = preference.ssid ?: stringResource(R.string.networks_cellular)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag(NetworksTestTags.preference(preference.id)),
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
                    text = stringResource(R.string.networks_remove, name),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    ) {
        PreferenceCard(
            preference = preference,
            name = name,
            onRename = onRename,
            onSetEnabled = onSetEnabled,
        )
    }
}

@Composable
private fun PreferenceCard(
    preference: NetworkPreference,
    name: String,
    onRename: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag(NetworksTestTags.preferenceName(preference.id))
                    .let { base ->
                        if (preference.ssid != null) base.clickable(onClick = onRename) else base
                    },
            ) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        if (preference.desiredState == TunnelState.ENABLED) {
                            R.string.networks_state_enabled
                        } else {
                            R.string.networks_state_disabled
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = preference.desiredState == TunnelState.ENABLED,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.testTag(NetworksTestTags.preferenceSwitch(preference.id)),
            )
        }
    }
}

/**
 * Explique pourquoi la localisation est demandée, **avant** de la demander.
 */
@Composable
private fun LocationRationaleCard(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NetworksTestTags.LOCATION_RATIONALE),
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
                text = stringResource(R.string.networks_location_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.networks_location_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = onGrant,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(NetworksTestTags.LOCATION_GRANT),
            ) {
                Text(stringResource(R.string.networks_location_grant))
            }
        }
    }
}

/** Édition en cours : [id] à `null` pour une création. */
private data class EditingState(val id: Long?, val value: String)

/**
 * Saisie d'un réseau : le nom, et — à la création seulement — sa volonté.
 *
 * « Coupé » est le choix pré-rempli : déclarer un réseau, c'est d'abord le
 * geste de confiance d'hier. Au renommage, la volonté ne se touche pas ici :
 * elle a son interrupteur sur la carte.
 */
@Composable
private fun PreferenceDialog(
    initialValue: String,
    withBehaviourChoice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    var tunnelEnabled by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.networks_ssid_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.testTag(NetworksTestTags.DIALOG_FIELD),
                )
                if (withBehaviourChoice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.networks_dialog_enabled),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = tunnelEnabled,
                            onCheckedChange = { tunnelEnabled = it },
                            modifier = Modifier.testTag(NetworksTestTags.DIALOG_SWITCH),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value, tunnelEnabled) },
                modifier = Modifier.testTag(NetworksTestTags.DIALOG_CONFIRM),
            ) {
                Text(stringResource(R.string.networks_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.networks_cancel))
            }
        },
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NetworksTestTags.ERROR),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
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
                Text(stringResource(R.string.networks_cancel))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworksScreenPreview() {
    AppTheme(dynamicColor = false) {
        NetworksScreen(
            uiState = NetworksUiState(
                preferences = listOf(
                    NetworkPreference(
                        id = 1,
                        key = NetworkPreferenceKey("wifi:maison"),
                        ssid = "Maison",
                        desiredState = TunnelState.DISABLED,
                        epochMillis = 0,
                    ),
                    NetworkPreference(
                        id = 2,
                        key = NetworkPreferenceKey.Cellular,
                        ssid = null,
                        desiredState = TunnelState.ENABLED,
                        epochMillis = 0,
                    ),
                ),
                currentSsid = "Aéroport CDG",
            ),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworksScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        NetworksScreen(
            uiState = NetworksUiState(error = NetworksError.DUPLICATE),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }
}
