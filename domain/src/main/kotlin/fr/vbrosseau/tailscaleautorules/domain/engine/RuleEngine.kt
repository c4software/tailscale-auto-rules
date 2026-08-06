package fr.vbrosseau.tailscaleautorules.domain.engine

import fr.vbrosseau.tailscaleautorules.domain.rule.Rule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleContext

/**
 * Trie les règles, les évalue, et retient la première décision ferme.
 *
 * Le moteur ne connaît **aucune** règle en particulier : il reçoit un ensemble
 * et n'en manipule que le contrat. C'est ce qui permet d'ajouter une règle sans
 * jamais modifier cette classe.
 *
 * L'évaluation est paresseuse : dès qu'une règle se prononce, les suivantes ne
 * sont pas évaluées. Ce n'est pas une optimisation mais une **sémantique** —
 * la priorité définit qui a le dernier mot.
 */
class RuleEngine(private val rules: Set<Rule>) {
    fun evaluate(context: RuleContext): RuleEvaluation =
        rules
            .map { rule -> rule to context.settingsFor(rule) }
            .filter { (_, settings) -> settings.isEnabled }
            // Le départage par identifiant rend l'ordre total : à priorité
            // égale, deux exécutions donnent le même résultat, quel que soit
            // l'ordre d'itération de l'ensemble.
            .sortedWith(compareBy({ (_, settings) -> settings.priority }, { (rule, _) -> rule.id.value }))
            .firstNotNullOfOrNull { (rule, _) ->
                val decision = rule.evaluate(context)
                if (decision.isFirm) RuleEvaluation(decision, rule.id) else null
            }
            ?: RuleEvaluation.NoDecision
}
