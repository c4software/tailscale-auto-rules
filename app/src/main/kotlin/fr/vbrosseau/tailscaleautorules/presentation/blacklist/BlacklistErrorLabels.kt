package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R

/**
 * Libellés des échecs.
 *
 * Le `when` est exhaustif sans branche `else` : un nouveau cas d'erreur ne peut
 * pas être introduit sans qu'on décide ici comment le raconter.
 */
@StringRes
fun BlacklistError.labelRes(): Int = when (this) {
    BlacklistError.DUPLICATE -> R.string.blacklist_error_duplicate
    BlacklistError.BLANK -> R.string.blacklist_error_blank
    BlacklistError.UNKNOWN -> R.string.blacklist_error_unknown
}
