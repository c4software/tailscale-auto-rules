package fr.vbrosseau.tailscaleautorules.presentation.networks

import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R

/**
 * Libellés des échecs.
 *
 * Le `when` est exhaustif sans branche `else` : un nouveau cas d'erreur ne peut
 * pas être introduit sans qu'on décide ici comment le raconter.
 */
@StringRes
fun NetworksError.labelRes(): Int = when (this) {
    NetworksError.DUPLICATE -> R.string.networks_error_duplicate
    NetworksError.BLANK -> R.string.networks_error_blank
    NetworksError.UNKNOWN -> R.string.networks_error_unknown
}
