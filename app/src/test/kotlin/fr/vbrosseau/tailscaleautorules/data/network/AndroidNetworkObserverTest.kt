package fr.vbrosseau.tailscaleautorules.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Couvre la traduction état système → [NetworkContext].
 *
 * **Les capacités sont livrées par callback, jamais posées.** C'est la seule
 * façon d'obtenir un SSID non expurgé, donc la seule que l'implémentation
 * emploie ; un test qui se contenterait de `setNetworkCapabilities` éprouverait
 * un chemin que la production n'emprunte pas — et attendrait indéfiniment un
 * callback qui ne vient jamais.
 *
 * Le debounce n'est pas testé ici : c'est un opérateur du domaine, éprouvé en
 * temps virtuel par `NetworkContextFlowTest`, sans Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidNetworkObserverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(ConnectivityManager::class.java)

    /**
     * L'observateur partage l'ordonnanceur du test.
     *
     * Sans ce partage, son délai d'attente courrait sur une horloge virtuelle
     * que rien ne fait avancer : le test se bloquerait au lieu d'échouer.
     */
    private fun TestScope.observer() =
        AndroidNetworkObserver(context, UnconfinedTestDispatcher(testScheduler))

    private fun capabilities(transport: Int, validated: Boolean): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also {
            Shadows.shadowOf(it).addTransportType(transport)
            if (validated) {
                Shadows.shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

    /** Livre des capacités à tous les callbacks inscrits, un réseau par lot. */
    private fun deliver(vararg capabilities: NetworkCapabilities) {
        val registered = Shadows.shadowOf(connectivityManager).networkCallbacks.toList()

        capabilities.forEachIndexed { index, networkCapabilities ->
            val network = ShadowNetwork.newInstance(FIRST_NET_ID + index)
            registered.forEach { it.onCapabilitiesChanged(network, networkCapabilities) }
        }
    }

    /**
     * Démarre une capture, laisse l'inscription se faire, livre les capacités,
     * puis laisse retomber la rafale.
     *
     * L'ordre importe : livrer avant l'inscription ne toucherait personne, et
     * décider avant la fin de la fenêtre retiendrait le premier réseau livré
     * plutôt que celui qui porte la connexion.
     */
    private fun TestScope.captureAfterDelivering(
        observer: AndroidNetworkObserver,
        vararg capabilities: NetworkCapabilities,
    ): Deferred<NetworkContext> = async { observer.current() }.also {
        runCurrent()
        deliver(*capabilities)
        advanceTimeBy(SETTLE_MARGIN)
    }

    private fun setAirplaneMode(enabled: Boolean) {
        Settings.Global.putInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            if (enabled) 1 else 0,
        )
    }

    @Test
    fun aWifiNetworkIsTranslatedAsSuch() = runTest {
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = true),
        ).await()

        assertEquals(NetworkTransport.WIFI, networkContext.transport)
        assertTrue(networkContext.isInternetValidated)
        assertTrue(networkContext.isUsable)
    }

    @Test
    fun aCellularNetworkIsTranslatedAsSuch() = runTest {
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true),
        ).await()

        assertEquals(NetworkTransport.CELLULAR, networkContext.transport)
    }

    @Test
    fun anEthernetNetworkIsTranslatedAsSuch() = runTest {
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_ETHERNET, validated = true),
        ).await()

        assertEquals(NetworkTransport.ETHERNET, networkContext.transport)
    }

    @Test
    fun anUnvalidatedNetworkIsConnectedButNotUsable() = runTest {
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = false),
        ).await()

        assertTrue(networkContext.isConnected)
        assertFalse(
            networkContext.isUsable,
            "Sans validation Internet, le réseau est inexploitable.",
        )
    }

    @Test
    fun anUnknownSsidIsReportedAsUnavailableRatherThanAsAValue() = runTest {
        // Sans permission de localisation, le système renvoie une valeur de
        // repli qui ne doit jamais être prise pour un vrai SSID.
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = true),
        ).await()

        assertNull(networkContext.ssid)
    }

    @Test
    fun theTunnelNeverMasksThePhysicalNetworkItRunsOn() = runTest {
        // Une fois monté, le tunnel devient le réseau actif. S'il était retenu,
        // toutes les règles décideraient sur « autre transport » et
        // s'abstiendraient : l'automatisation se figerait dès la première
        // activation.
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true),
            capabilities(NetworkCapabilities.TRANSPORT_VPN, validated = true),
        ).await()

        assertEquals(NetworkTransport.CELLULAR, networkContext.transport)
    }

    @Test
    fun wifiWinsOverCellularWhenBothAreStillValidated() = runTest {
        // Android conserve le cellulaire actif et validé plusieurs minutes
        // après une bascule vers le Wi-Fi. Départager sur la seule validation
        // laissait gagner le cellulaire, livré en premier : le tunnel ne
        // réagissait qu'au démontage effectif du réseau mobile, très longtemps
        // après. C'est exactement ce qui a été constaté sur appareil.
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true),
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = true),
        ).await()

        assertEquals(NetworkTransport.WIFI, networkContext.transport)
    }

    @Test
    fun anUnvalidatedWifiDoesNotSupplantAWorkingCellularNetwork() = runTest {
        // La réciproque : tant que le Wi-Fi n'a pas confirmé son accès
        // Internet, basculer dessus couperait une connexion qui fonctionne.
        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true),
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = false),
        ).await()

        assertEquals(NetworkTransport.CELLULAR, networkContext.transport)
    }

    @Test
    fun airplaneModeIsReadFromTheSystemSetting() = runTest {
        setAirplaneMode(true)

        val networkContext = captureAfterDelivering(
            observer(),
            capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true),
        ).await()

        assertTrue(networkContext.isAirplaneModeOn)
    }

    @Test
    fun aCaptureThatReceivesNothingFallsBackRatherThanBlocking() = runTest {
        // Hors ligne ou en mode avion, aucun réseau ne correspond et aucun
        // callback n'arrive. Sans repli, une synchronisation manuelle
        // attendrait indéfiniment.
        //
        // Le repli interroge le système directement : le transport y est exact,
        // mais les capacités relues sont expurgées — d'où l'absence de SSID,
        // qui distingue ce chemin de celui du callback.
        Shadows.shadowOf(connectivityManager).setNetworkCapabilities(
            connectivityManager.activeNetwork,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, validated = true),
        )

        val capture = async { observer().current() }
        runCurrent()

        advanceTimeBy(5.seconds)

        val networkContext = capture.await()
        assertEquals(NetworkTransport.WIFI, networkContext.transport)
        assertNull(networkContext.ssid, "Les capacités relues sont expurgées du SSID.")
    }

    private companion object {
        const val FIRST_NET_ID = 100

        /** Dépasse confortablement la fenêtre de retombée de l'observateur. */
        val SETTLE_MARGIN = 1.seconds
    }
}
