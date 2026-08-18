package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionAttestationTest {
    private val clock = FakeClock(60_000)
    private val attestation = SessionAttestation(clock)

    @Test
    fun nothingIsAttestedBeforeAnyConfirmation() {
        assertFalse(attestation.attests(TunnelState.ENABLED))
        assertFalse(attestation.attests(TunnelState.DISABLED))
    }

    @Test
    fun aSettledConfirmationAttestsItsStateOnly() {
        attestation.confirm(TunnelState.DISABLED)
        clock.advanceBy(60_000)

        assertTrue(attestation.attests(TunnelState.DISABLED))
        assertFalse(
            attestation.attests(TunnelState.ENABLED),
            "Le constat vaut pour la décision confirmée, pas pour une autre.",
        )
    }

    @Test
    fun aFreshConfirmationDoesNotAttestYet() {
        // Même grâce que le journal : un client encore en train de restaurer
        // son propre état ne doit pas passer pour une main humaine.
        attestation.confirm(TunnelState.DISABLED)
        clock.advanceBy(2_000)

        assertFalse(attestation.attests(TunnelState.DISABLED))
    }

    @Test
    fun aNewConfirmationReplacesThePreviousOne() {
        attestation.confirm(TunnelState.DISABLED)
        clock.advanceBy(60_000)
        attestation.confirm(TunnelState.ENABLED)
        clock.advanceBy(60_000)

        assertTrue(attestation.attests(TunnelState.ENABLED))
        assertFalse(attestation.attests(TunnelState.DISABLED))
    }
}
