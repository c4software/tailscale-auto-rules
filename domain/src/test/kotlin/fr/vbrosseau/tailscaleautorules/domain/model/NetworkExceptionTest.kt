package fr.vbrosseau.tailscaleautorules.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NetworkExceptionTest {
    @Test
    fun aWifiKeyUsesTheCanonicalSsid() {
        val context =
            NetworkContext(
                transport = NetworkTransport.WIFI,
                isInternetValidated = true,
                ssid = "  Maison ",
            )

        assertEquals(NetworkExceptionKey("wifi:maison"), NetworkExceptionKey.from(context))
    }

    @Test
    fun cellularSharesASingleGlobalKey() {
        // SPECS.md §4.5 : le cellulaire n'expose aucun identifiant, l'exception
        // vaut pour toutes les données mobiles.
        val context =
            NetworkContext(
                transport = NetworkTransport.CELLULAR,
                isInternetValidated = true,
            )

        assertEquals(NetworkExceptionKey.Cellular, NetworkExceptionKey.from(context))
    }

    @Test
    fun anUnreadableSsidYieldsNoKey() {
        val context =
            NetworkContext(
                transport = NetworkTransport.WIFI,
                isInternetValidated = true,
                ssid = null,
            )

        assertNull(NetworkExceptionKey.from(context))
    }

    @Test
    fun theKeyIgnoresInternetValidation() {
        // La clé est une identité, pas un état : l'activation du VPN fait
        // fugacement perdre sa validation au réseau porteur, et exiger la
        // validation faisait rater la capture du geste (SPECS.md §4.5).
        val wifi =
            NetworkContext(
                transport = NetworkTransport.WIFI,
                isInternetValidated = false,
                ssid = "Maison",
            )
        val cellular = NetworkContext(transport = NetworkTransport.CELLULAR)

        assertEquals(NetworkExceptionKey("wifi:maison"), NetworkExceptionKey.from(wifi))
        assertEquals(NetworkExceptionKey.Cellular, NetworkExceptionKey.from(cellular))
    }

    @Test
    fun uncoveredTransportsYieldNoKey() {
        assertNull(NetworkExceptionKey.from(NetworkContext.Disconnected))
        assertNull(
            NetworkExceptionKey.from(
                NetworkContext(
                    transport = NetworkTransport.ETHERNET,
                    isInternetValidated = true,
                ),
            ),
        )
        assertNull(
            NetworkExceptionKey.from(
                NetworkContext(
                    transport = NetworkTransport.OTHER,
                    isInternetValidated = true,
                ),
            ),
        )
    }

    @Test
    fun aBlankKeyIsRejected() {
        assertFailsWith<IllegalArgumentException> { NetworkExceptionKey("  ") }
    }

    @Test
    fun anExceptionNeverMemorizesAnUnknownState() {
        assertFailsWith<IllegalArgumentException> {
            NetworkException(
                id = 1L,
                key = NetworkExceptionKey.Cellular,
                ssid = null,
                desiredState = TunnelState.UNKNOWN,
                epochMillis = 0L,
            )
        }
    }

    @Test
    fun theDisplaySsidAccompaniesExactlyTheWifiKeys() {
        // Une clé Wi-Fi sans SSID d'affichage, ou l'inverse, trahirait une
        // dérivation incohérente au moment de la capture.
        assertFailsWith<IllegalArgumentException> {
            NetworkException(
                id = 1L,
                key = NetworkExceptionKey("wifi:maison"),
                ssid = null,
                desiredState = TunnelState.ENABLED,
                epochMillis = 0L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkException(
                id = 1L,
                key = NetworkExceptionKey.Cellular,
                ssid = "Maison",
                desiredState = TunnelState.ENABLED,
                epochMillis = 0L,
            )
        }
    }

    @Test
    fun aBlankDisplaySsidIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            NetworkException(
                id = 1L,
                key = NetworkExceptionKey("wifi:maison"),
                ssid = "  ",
                desiredState = TunnelState.ENABLED,
                epochMillis = 0L,
            )
        }
    }

    @Test
    fun aWellFormedExceptionIsAccepted() {
        val exception =
            NetworkException(
                id = 7L,
                key = NetworkExceptionKey("wifi:maison"),
                ssid = "Maison",
                desiredState = TunnelState.DISABLED,
                epochMillis = 42L,
            )

        assertEquals(TunnelState.DISABLED, exception.desiredState)
    }
}
