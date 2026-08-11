package fr.vbrosseau.tailscaleautorules.presentation

import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId

/**
 * Traduit un [RuleId] en libellé lisible.
 *
 * C'est ici, et nulle part ailleurs, que la frontière est franchie : le domaine
 * ne porte aucun texte, parce qu'il ne connaît pas la langue de l'utilisateur.
 *
 * Le repli sur [R.string.rule_unknown] couvre le cas d'une entrée de journal
 * écrite par une version qui connaissait une règle depuis retirée. Faire
 * échouer l'affichage pour une ligne d'historique serait disproportionné.
 */
@StringRes
fun RuleId.labelRes(): Int = when (value) {
    "airplane-mode" -> R.string.rule_airplane_mode
    // L'ancien identifiant reste traduisible : le journal persistant porte
    // des entrées écrites sous « network-exception » avant la fusion.
    "network-preference", "network-exception" -> R.string.rule_network_preference
    "blacklisted-wifi" -> R.string.rule_blacklisted_wifi
    "other-wifi" -> R.string.rule_other_wifi
    "mobile-network" -> R.string.rule_mobile_network
    else -> R.string.rule_unknown
}
