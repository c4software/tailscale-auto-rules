package fr.vbrosseau.tailscaleautorules.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleDecisionTest {
    @Test
    fun onlyAnAbstentionIsNotFirm() {
        assertTrue(RuleDecision.ENABLE.isFirm)
        assertTrue(RuleDecision.DISABLE.isFirm)
        assertFalse(RuleDecision.NO_DECISION.isFirm)
    }

    @Test
    fun exactlyOneDecisionAllowsTheEngineToContinue() {
        // Le moteur s'arrête à la première décision ferme : il ne doit exister
        // qu'une seule façon de ne pas se prononcer.
        assertEquals(
            listOf(RuleDecision.NO_DECISION),
            RuleDecision.entries.filterNot { it.isFirm },
        )
    }
}
