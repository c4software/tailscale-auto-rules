package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision

/**
 * Stratégie de décision autonome.
 *
 * Une règle ne connaît **ni les autres règles, ni le moteur, ni l'état courant
 * du tunnel**. Elle répond à une seule question : « au vu de ce contexte,
 * qu'est-ce que je préconise ? »
 *
 * [evaluate] est **pure** : aucune entrée/sortie, aucune horloge, aucune
 * journalisation. Tout ce dont la règle a besoin est dans [RuleContext]. C'est
 * cette contrainte qui rend une couverture exhaustive atteignable sans
 * échafaudage.
 *
 * Ajouter une règle consiste à écrire une classe et à l'enregistrer dans
 * l'injection : le moteur n'est jamais modifié.
 */
interface Rule {
    /** Identité stable, jamais renommée. */
    val id: RuleId

    /**
     * Réglages appliqués tant que l'utilisateur n'a rien changé.
     *
     * Priorité croissante : plus la valeur est basse, plus la règle est
     * évaluée tôt.
     */
    val defaultSettings: RuleSettings

    /** Préconisation de la règle, ou [RuleDecision.NO_DECISION] si elle ne se prononce pas. */
    fun evaluate(context: RuleContext): RuleDecision
}

/**
 * Tout ce qu'une règle peut consulter : l'état du réseau et la configuration
 * de l'utilisateur.
 *
 * Regrouper les deux ici, plutôt que d'injecter des dépendances dans chaque
 * règle, est ce qui garde [Rule.evaluate] pure. Une règle future qui a besoin
 * d'une donnée absente l'ajoute à ce type — sans toucher aux règles existantes.
 *
 * @param network instantané de l'état réseau.
 * @param blacklistedSsids SSID que l'utilisateur a marqués comme de confiance.
 * @param settings réglages surchargés par l'utilisateur, par identifiant de règle.
 */
data class RuleContext(
    val network: NetworkContext,
    val blacklistedSsids: Set<String> = emptySet(),
    val settings: Map<RuleId, RuleSettings> = emptyMap(),
) {
    /** Réglages effectifs d'une règle : ceux de l'utilisateur, à défaut ceux de la règle. */
    fun settingsFor(rule: Rule): RuleSettings = settings[rule.id] ?: rule.defaultSettings
}
