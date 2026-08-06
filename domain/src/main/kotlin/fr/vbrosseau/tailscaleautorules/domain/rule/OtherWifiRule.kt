package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision

/**
 * Wi-Fi non reconnu → tunnel activé.
 *
 * Elle n'interroge pas la liste de confiance : [BlacklistedWifiRule], plus
 * prioritaire, a déjà tranché ce cas. Une règle qui vérifierait à nouveau
 * dupliquerait une connaissance, et les deux pourraient diverger.
 *
 * Un réseau sans accès Internet confirmé ne déclenche rien : activer un tunnel
 * sur un réseau inexploitable n'aurait aucun effet utile.
 */
class OtherWifiRule : Rule {
    override val id = RuleId("other-wifi")

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.OTHER_WIFI,
        )

    override fun evaluate(context: RuleContext): RuleDecision =
        if (context.network.transport == NetworkTransport.WIFI && context.network.isUsable) {
            RuleDecision.ENABLE
        } else {
            RuleDecision.NO_DECISION
        }
}
