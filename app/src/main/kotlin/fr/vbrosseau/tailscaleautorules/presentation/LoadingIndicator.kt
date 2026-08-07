package fr.vbrosseau.tailscaleautorules.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing

/**
 * Indicateur affiché tant qu'un écran n'a pas reçu son premier état constaté.
 *
 * Rendre l'état par défaut d'un `UiState` ferait apparaître un écran « vide »
 * — liste sans entrée, tunnel inconnu, réglages d'usine — le temps que Room,
 * DataStore ou le système livrent leur première valeur. Ce vide se lirait
 * comme une donnée, pas comme une attente.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(LoadingTestTags.INDICATOR))
    }
}
