package fr.vbrosseau.tailscaleautorules.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Couvre la traduction état système → [NetworkContext].
 *
 * Le debounce n'est pas testé ici : c'est un opérateur du domaine, éprouvé en
 * temps virtuel par `NetworkContextFlowTest`, sans Android.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidNetworkObserverTest {

    private lateinit var context: Context
    private lateinit var observer: AndroidNetworkObserver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        observer = AndroidNetworkObserver(context, UnconfinedTestDispatcher())
    }

    private fun simulateNetwork(transport: Int, validated: Boolean) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addTransportType(transport)
        if (validated) {
            Shadows.shadowOf(capabilities)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        Shadows.shadowOf(connectivityManager)
            .setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
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
        simulateNetwork(NetworkCapabilities.TRANSPORT_WIFI, validated = true)

        val networkContext = observer.current()

        assertEquals(NetworkTransport.WIFI, networkContext.transport)
        assertTrue(networkContext.isInternetValidated)
        assertTrue(networkContext.isUsable)
    }

    @Test
    fun aCellularNetworkIsTranslatedAsSuch() = runTest {
        simulateNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true)

        assertEquals(NetworkTransport.CELLULAR, observer.current().transport)
    }

    @Test
    fun anEthernetNetworkIsTranslatedAsSuch() = runTest {
        simulateNetwork(NetworkCapabilities.TRANSPORT_ETHERNET, validated = true)

        assertEquals(NetworkTransport.ETHERNET, observer.current().transport)
    }

    @Test
    fun anUnvalidatedNetworkIsConnectedButNotUsable() = runTest {
        simulateNetwork(NetworkCapabilities.TRANSPORT_WIFI, validated = false)

        val networkContext = observer.current()

        assertTrue(networkContext.isConnected)
        assertFalse(networkContext.isUsable, "Sans validation Internet, le réseau est inexploitable.")
    }

    @Test
    fun anUnknownSsidIsReportedAsUnavailableRatherThanAsAValue() = runTest {
        simulateNetwork(NetworkCapabilities.TRANSPORT_WIFI, validated = true)

        // Sans permission de localisation, le système renvoie une valeur de
        // repli qui ne doit jamais être prise pour un vrai SSID.
        assertNull(observer.current().ssid)
    }

    @Test
    fun airplaneModeIsReadFromTheSystemSetting() = runTest {
        simulateNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, validated = true)
        setAirplaneMode(true)

        assertTrue(observer.current().isAirplaneModeOn)

        setAirplaneMode(false)

        assertFalse(observer.current().isAirplaneModeOn)
    }
}
