package fr.vbrosseau.tailscaleautorules.domain.tailscale

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le Fake sert de référence à tous les tests en aval : s'il ment, ils mentent
 * tous. Il est donc couvert comme du code de production.
 */
class FakeTailscaleControllerTest {
    @Test
    fun anAvailableControllerStartsDisabledByDefault() =
        runTest {
            val controller = FakeTailscaleController()

            assertTrue(controller.isAvailable())
            assertFalse(controller.isRunning())
        }

    @Test
    fun aTransmittedCommandChangesTheState() =
        runTest {
            val controller = FakeTailscaleController()

            assertTrue(controller.enable().isSuccess)
            assertTrue(controller.isRunning())

            assertTrue(controller.disable().isSuccess)
            assertFalse(controller.isRunning())
        }

    @Test
    fun commandsAreCountedWhateverTheirOutcome() =
        runTest {
            val controller = FakeTailscaleController(available = false)

            controller.enable()
            controller.enable()
            controller.disable()

            assertEquals(2, controller.enableCount)
            assertEquals(1, controller.disableCount)
        }

    @Test
    fun anUnavailableClientFailsEveryCommandWithoutChangingTheState() =
        runTest {
            val controller = FakeTailscaleController(running = true, available = false)

            val result = controller.enable()

            assertIs<TailscaleUnavailableException>(result.exceptionOrNull())
            assertTrue(controller.isRunning(), "L'état ne doit pas bouger sans client.")
        }

    @Test
    fun anInjectedFailureAppliesOnceAndLeavesTheStateUntouched() =
        runTest {
            val controller = FakeTailscaleController()
            val cause = IllegalStateException("diffusion refusée")
            controller.nextFailure = cause

            val failed = controller.enable()
            assertEquals(cause, failed.exceptionOrNull())
            assertFalse(controller.isRunning())

            // L'échec est consommé : la commande suivante aboutit.
            assertTrue(controller.enable().isSuccess)
            assertTrue(controller.isRunning())
        }

    @Test
    fun availabilityCanBeToggledDuringATest() =
        runTest {
            val controller = FakeTailscaleController()
            assertTrue(controller.enable().isSuccess)

            controller.available = false

            assertIs<TailscaleUnavailableException>(controller.disable().exceptionOrNull())
        }
}
