package fr.vbrosseau.tailscaleautorules.data.tailscale

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.data.tailscale.AndroidTailscaleController.Companion.ACTION_CONNECT
import fr.vbrosseau.tailscaleautorules.data.tailscale.AndroidTailscaleController.Companion.ACTION_DISCONNECT
import fr.vbrosseau.tailscaleautorules.data.tailscale.AndroidTailscaleController.Companion.IPN_RECEIVER
import fr.vbrosseau.tailscaleautorules.data.tailscale.AndroidTailscaleController.Companion.TAILSCALE_PACKAGE
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleUnavailableException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidTailscaleControllerTest {

    private lateinit var context: Context
    private lateinit var controller: AndroidTailscaleController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = AndroidTailscaleController(context, UnconfinedTestDispatcher())
    }

    private fun installTailscale() {
        val packageInfo = PackageInfo().apply { packageName = TAILSCALE_PACKAGE }
        Shadows.shadowOf(context.packageManager).installPackage(packageInfo)
    }

    private fun sentBroadcasts(): List<Intent> =
        Shadows.shadowOf(context as Application).broadcastIntents

    @Test
    fun theClientIsReportedAbsentWhenThePackageIsNotInstalled() = runTest {
        assertEquals(false, controller.isAvailable())
    }

    @Test
    fun theClientIsReportedPresentOnceInstalled() = runTest {
        installTailscale()

        assertEquals(true, controller.isAvailable())
    }

    @Test
    fun enableTargetsTheOfficialReceiverExplicitly() = runTest {
        installTailscale()

        assertTrue(controller.enable().isSuccess)

        val intent = sentBroadcasts().last()
        assertEquals(ACTION_CONNECT, intent.action)
        // Une diffusion implicite n'atteindrait pas le receveur : le composant
        // doit être désigné nommément.
        assertEquals(TAILSCALE_PACKAGE, intent.component?.packageName)
        assertEquals(IPN_RECEIVER, intent.component?.className)
    }

    @Test
    fun disableTargetsTheOfficialReceiverExplicitly() = runTest {
        installTailscale()

        assertTrue(controller.disable().isSuccess)

        val intent = sentBroadcasts().last()
        assertEquals(ACTION_DISCONNECT, intent.action)
        assertEquals(IPN_RECEIVER, intent.component?.className)
    }

    @Test
    fun aCommandFailsExplicitlyWhenTheClientIsAbsent() = runTest {
        val before = sentBroadcasts().size

        val result = controller.enable()

        assertIs<TailscaleUnavailableException>(result.exceptionOrNull())
        assertEquals(before, sentBroadcasts().size, "Aucune diffusion ne doit partir.")
    }

    @Test
    fun noVpnTransportMeansTheTunnelIsNotRunning() = runTest {
        assertEquals(false, controller.isRunning())
    }
}
