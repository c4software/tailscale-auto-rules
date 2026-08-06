package fr.vbrosseau.tailscaleautorules.domain.engine

import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.rule.Contexts
import fr.vbrosseau.tailscaleautorules.domain.rule.Rule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleContext
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le moteur est testé avec des règles factices, jamais avec les vraies :
 * ce sont ses mécanismes — filtrage, tri, arrêt — qui sont éprouvés ici, pas
 * le métier des règles livrées.
 */
class RuleEngineTest {
    /** Règle déterministe, qui enregistre le fait d'avoir été évaluée. */
    private class StubRule(
        name: String,
        priority: Int,
        isEnabled: Boolean = true,
        private val decision: RuleDecision = RuleDecision.NO_DECISION,
    ) : Rule {
        override val id = RuleId(name)
        override val defaultSettings = RuleSettings(isEnabled = isEnabled, priority = priority)

        var wasEvaluated: Boolean = false
            private set

        override fun evaluate(context: RuleContext): RuleDecision {
            wasEvaluated = true
            return decision
        }
    }

    private val context = Contexts.none()

    @Test
    fun anEmptySetOfRulesYieldsNoDecision() {
        val evaluation = RuleEngine(emptySet()).evaluate(context)

        assertEquals(RuleDecision.NO_DECISION, evaluation.decision)
        assertNull(evaluation.ruleId)
    }

    @Test
    fun rulesThatAllAbstainYieldNoDecision() {
        val engine = RuleEngine(setOf(StubRule("a", 100), StubRule("b", 200)))

        assertEquals(RuleEvaluation.NoDecision, engine.evaluate(context))
    }

    @Test
    fun theFirstFirmDecisionWins() {
        val engine =
            RuleEngine(
                setOf(
                    StubRule("late", 300, decision = RuleDecision.ENABLE),
                    StubRule("early", 100, decision = RuleDecision.DISABLE),
                    StubRule("middle", 200, decision = RuleDecision.ENABLE),
                ),
            )

        val evaluation = engine.evaluate(context)

        assertEquals(RuleDecision.DISABLE, evaluation.decision)
        assertEquals(RuleId("early"), evaluation.ruleId)
    }

    @Test
    fun anAbstainingRuleLetsTheNextOneDecide() {
        val engine =
            RuleEngine(
                setOf(
                    StubRule("silent", 100),
                    StubRule("speaker", 200, decision = RuleDecision.ENABLE),
                ),
            )

        assertEquals(RuleId("speaker"), engine.evaluate(context).ruleId)
    }

    @Test
    fun rulesAfterTheDecisionAreNotEvaluated() {
        // L'arrêt anticipé n'est pas une optimisation : c'est la sémantique de
        // la priorité. Une règle en aval ne doit avoir aucun effet observable.
        val decider = StubRule("decider", 100, decision = RuleDecision.DISABLE)
        val successor = StubRule("successor", 200, decision = RuleDecision.ENABLE)

        RuleEngine(setOf(decider, successor)).evaluate(context)

        assertTrue(decider.wasEvaluated)
        assertTrue(!successor.wasEvaluated, "La règle suivante ne devait pas être évaluée.")
    }

    @Test
    fun aDisabledRuleIsNeverEvaluated() {
        val disabled = StubRule("disabled", 100, isEnabled = false, decision = RuleDecision.DISABLE)
        val enabled = StubRule("enabled", 200, decision = RuleDecision.ENABLE)

        val evaluation = RuleEngine(setOf(disabled, enabled)).evaluate(context)

        assertEquals(RuleId("enabled"), evaluation.ruleId)
        assertTrue(!disabled.wasEvaluated)
    }

    @Test
    fun allRulesDisabledYieldsNoDecision() {
        val engine =
            RuleEngine(
                setOf(StubRule("a", 100, isEnabled = false, decision = RuleDecision.DISABLE)),
            )

        assertEquals(RuleEvaluation.NoDecision, engine.evaluate(context))
    }

    @Test
    fun equalPrioritiesAreBrokenByIdentifierSoTheOrderIsTotal() {
        // Sans départage, l'ordre d'itération d'un Set rendrait le résultat
        // imprévisible d'une exécution à l'autre.
        val engine =
            RuleEngine(
                setOf(
                    StubRule("zebra", 100, decision = RuleDecision.ENABLE),
                    StubRule("alpha", 100, decision = RuleDecision.DISABLE),
                ),
            )

        repeat(10) {
            assertEquals(RuleId("alpha"), engine.evaluate(context).ruleId)
        }
    }

    @Test
    fun userSettingsOverrideTheDefaultPriority() {
        val first = StubRule("first", 100, decision = RuleDecision.DISABLE)
        val second = StubRule("second", 200, decision = RuleDecision.ENABLE)
        val reordered =
            context.copy(
                settings =
                    mapOf(
                        RuleId("second") to RuleSettings(isEnabled = true, priority = 50),
                    ),
            )

        assertEquals(RuleId("second"), RuleEngine(setOf(first, second)).evaluate(reordered).ruleId)
    }

    @Test
    fun userSettingsCanDisableARuleThatIsEnabledByDefault() {
        val rule = StubRule("rule", 100, decision = RuleDecision.DISABLE)
        val muted =
            context.copy(
                settings = mapOf(RuleId("rule") to RuleSettings(isEnabled = false, priority = 100)),
            )

        assertEquals(RuleEvaluation.NoDecision, RuleEngine(setOf(rule)).evaluate(muted))
    }

    @Test
    fun aFirmDecisionAlwaysNamesItsRule() {
        assertFailsWith<IllegalArgumentException> {
            RuleEvaluation(RuleDecision.ENABLE, ruleId = null)
        }
        assertFailsWith<IllegalArgumentException> {
            RuleEvaluation(RuleDecision.NO_DECISION, ruleId = RuleId("a"))
        }
    }
}
