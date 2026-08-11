package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState

/**
 * Réseau dérogé → l'état que l'utilisateur y a choisi (SPECS.md §4.5).
 *
 * Placée sous le mode avion — jamais de rejeu en avion — mais au-dessus des
 * règles Wi-Fi et mobile : le choix explicite de l'utilisateur prime sur le
 * comportement par défaut, y compris sur un réseau blacklisté.
 *
 * La dérivation de la clé porte déjà les abstentions — SSID illisible,
 * transport non couvert : sans identité stable, l'exception pourrait viser un
 * autre réseau que celui du geste d'origine. La validation Internet n'entre
 * pas en compte, comme pour la blacklist (SPECS.md §4.2) : le choix de
 * l'utilisateur ne dépend pas de l'accès Internet, et le VPN qui monte fait
 * fugacement perdre sa validation au réseau porteur.
 */
class NetworkPreferenceRule : Rule {
    override val id = Id

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.NETWORK_EXCEPTION,
        )

    override fun evaluate(context: RuleContext): RuleDecision {
        val key = NetworkPreferenceKey.from(context.network) ?: return RuleDecision.NO_DECISION

        return when (context.networkPreferences[key]) {
            TunnelState.ENABLED -> RuleDecision.ENABLE
            TunnelState.DISABLED -> RuleDecision.DISABLE
            TunnelState.UNKNOWN, null -> RuleDecision.NO_DECISION
        }
    }

    companion object {
        /**
         * Exposé pour les lecteurs qui désignent la règle sans l'instancier :
         * la capture du geste journalise sous cet identifiant, et la détection
         * d'un nouveau geste s'y compare.
         */
        val Id = RuleId("network-preference")
    }
}
