package fr.vbrosseau.tailscaleautorules.domain.model

import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JournalEntryTest {
    @Test
    fun anEntryAttestsAnActualChange() {
        val entry =
            JournalEntry(
                id = 1,
                epochMillis = 1_000,
                previousState = TunnelState.DISABLED,
                newState = TunnelState.ENABLED,
                ruleId = RuleId("mobile-network"),
            )

        assertEquals(TunnelState.ENABLED, entry.newState)
    }

    @Test
    fun anEntryWithoutChangeIsRejected() {
        // Le journal répond à « que s'est-il passé », pas à « qu'a-t-on
        // envisagé » : une confirmation d'état n'y a pas sa place.
        TunnelState.entries.forEach { state ->
            assertFailsWith<IllegalArgumentException> {
                JournalEntry(1, 1_000, state, state, RuleId("a"))
            }
        }
    }

    @Test
    fun aTransitionFromAnUnknownStateIsValid() {
        // Au premier lancement, l'état précédent est indéterminé : ce cas doit
        // pouvoir être consigné.
        val entry = JournalEntry(1, 0, TunnelState.UNKNOWN, TunnelState.ENABLED, RuleId("a"))

        assertEquals(TunnelState.UNKNOWN, entry.previousState)
    }
}
