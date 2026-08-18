package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision

/**
 * Wi-Fi non reconnu → tunnel activé.
 *
 * Elle n'interroge pas les préférences : [NetworkPreferenceRule], plus
 * prioritaire, a déjà tranché ce cas. Une règle qui vérifierait à nouveau
 * dupliquerait une connaissance, et les deux pourraient diverger.
 *
 * Un réseau sans accès Internet confirmé ne déclenche rien : activer un tunnel
 * sur un réseau inexploitable n'aurait aucun effet utile.
 *
 * Un SSID **expurgé** fait aussi s'abstenir : la permission est accordée, donc
 * une préférence « toujours coupé » peut viser ce réseau, mais la lecture
 * courante ne permet pas de le savoir. Constaté au boot, où le cycle sans
 * service de type « localisation » activait le tunnel sur le Wi-Fi de
 * confiance. Un SSID indisponible faute de permission reste en revanche un
 * `ENABLE` : l'utilisateur a renoncé à identifier ses réseaux, dans le doute on
 * protège la connexion (SPECS.md §4.2).
 */
class OtherWifiRule : Rule {
    override val id = RuleId("other-wifi")

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.OTHER_WIFI,
        )

    override fun evaluate(context: RuleContext): RuleDecision =
        if (context.network.transport == NetworkTransport.WIFI &&
            context.network.isUsable &&
            !context.network.isSsidRedacted
        ) {
            RuleDecision.ENABLE
        } else {
            RuleDecision.NO_DECISION
        }
}
