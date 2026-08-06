package fr.vbrosseau.tailscaleautorules.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController

/**
 * Barre de navigation entre les quatre destinations.
 *
 * Elle est pilotée par l'énumération [AppDestination] : ajouter une destination
 * ne demande aucune modification ici.
 */
@Composable
fun AppNavigationBar(
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = AppDestination.forRoute(currentRoute)

    NavigationBar(modifier = modifier) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                modifier = Modifier.testTag(NavigationTestTags.item(destination)),
            )
        }
    }
}

/**
 * Navigue vers une destination de la barre.
 *
 * `launchSingleTop` et le retour à la racine évitent d'empiler indéfiniment les
 * destinations : sans eux, dix allers-retours produiraient dix entrées dans la
 * pile de retour.
 */
fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
