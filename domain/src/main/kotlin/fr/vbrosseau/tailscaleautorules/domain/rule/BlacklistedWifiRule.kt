package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.asSsidKey

/**
 * Wi-Fi de confiance → tunnel désactivé.
 *
 * Aucun test de transport n'est nécessaire : l'invariant de `NetworkContext`
 * garantit qu'un SSID renseigné implique un transport Wi-Fi. Le vérifier à
 * nouveau ici serait une redondance que rien ne maintiendrait cohérente.
 *
 * Un SSID indisponible est traité comme **non** blacklisté : la règle
 * s'abstient et laisse [OtherWifiRule] activer le tunnel. Le choix est
 * délibéré et documenté en SPECS.md §4.2 — dans le doute, on protège la
 * connexion plutôt que de l'exposer.
 */
class BlacklistedWifiRule : Rule {
    override val id = RuleId("blacklisted-wifi")

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.BLACKLISTED_WIFI,
        )

    override fun evaluate(context: RuleContext): RuleDecision {
        val ssid = context.network.ssid ?: return RuleDecision.NO_DECISION
        val isBlacklisted = context.blacklistedSsids.any { it.asSsidKey() == ssid.asSsidKey() }

        return if (isBlacklisted) RuleDecision.DISABLE else RuleDecision.NO_DECISION
    }
}
