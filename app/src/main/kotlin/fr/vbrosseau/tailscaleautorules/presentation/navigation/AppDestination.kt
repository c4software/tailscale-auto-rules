package fr.vbrosseau.tailscaleautorules.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R

/**
 * Destinations visibles dans la barre de navigation.
 *
 * Rassembler route, libellés et icône ici évite qu'ils divergent : ajouter une
 * destination consiste à ajouter une entrée, et la barre suit.
 *
 * Deux libellés, et non un seul : la barre n'a de place que pour une ligne de
 * texte. « Réseaux de confiance » y débordait sur deux lignes, ce qui remontait
 * son icône par rapport aux trois autres. Le titre de l'écran garde en revanche
 * le libellé complet, qui seul est explicite.
 */
enum class AppDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    HOME(
        route = AppRoutes.HOME,
        labelRes = R.string.destination_home,
        shortLabelRes = R.string.destination_short_home,
        iconRes = R.drawable.ic_nav_home,
    ),
    BLACKLIST(
        route = AppRoutes.BLACKLIST,
        labelRes = R.string.destination_blacklist,
        shortLabelRes = R.string.destination_short_blacklist,
        iconRes = R.drawable.ic_nav_wifi,
    ),
    JOURNAL(
        route = AppRoutes.JOURNAL,
        labelRes = R.string.destination_journal,
        shortLabelRes = R.string.destination_short_journal,
        iconRes = R.drawable.ic_nav_journal,
    ),
    SETTINGS(
        route = AppRoutes.SETTINGS,
        labelRes = R.string.destination_settings,
        shortLabelRes = R.string.destination_short_settings,
        iconRes = R.drawable.ic_nav_settings,
    ),
    ;

    companion object {
        fun forRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
