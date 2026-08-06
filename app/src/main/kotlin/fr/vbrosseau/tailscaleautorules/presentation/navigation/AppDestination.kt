package fr.vbrosseau.tailscaleautorules.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R

/**
 * Destinations visibles dans la barre de navigation.
 *
 * Rassembler route, libellé et icône ici évite qu'ils divergent : ajouter une
 * destination consiste à ajouter une entrée, et la barre suit.
 */
enum class AppDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    HOME(AppRoutes.HOME, R.string.destination_home, R.drawable.ic_nav_home),
    BLACKLIST(AppRoutes.BLACKLIST, R.string.destination_blacklist, R.drawable.ic_nav_wifi),
    JOURNAL(AppRoutes.JOURNAL, R.string.destination_journal, R.drawable.ic_nav_journal),
    SETTINGS(AppRoutes.SETTINGS, R.string.destination_settings, R.drawable.ic_nav_settings),
    ;

    companion object {
        fun forRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
