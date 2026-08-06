package fr.vbrosseau.tailscaleautorules.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkContextTest {
    @Test
    fun anUnspecifiedContextIsNeitherInAirplaneModeNorValidated() {
        val context = NetworkContext(transport = NetworkTransport.CELLULAR)

        assertFalse(context.isAirplaneModeOn)
        assertFalse(context.isInternetValidated)
        assertNull(context.ssid)
    }

    @Test
    fun onlyTheAbsenceOfTransportMakesTheContextDisconnected() {
        assertFalse(NetworkContext(NetworkTransport.NONE).isConnected)

        NetworkTransport.entries
            .filter { it != NetworkTransport.NONE }
            .forEach { transport ->
                assertTrue(
                    NetworkContext(transport).isConnected,
                    "Le transport $transport devrait être considéré comme connecté.",
                )
            }
    }

    @Test
    fun aNetworkIsUsableOnlyWhenConnectedAndValidated() {
        assertTrue(
            NetworkContext(NetworkTransport.WIFI, isInternetValidated = true).isUsable,
        )
        // Associé mais sans accès Internet confirmé : traité comme inutilisable.
        assertFalse(
            NetworkContext(NetworkTransport.WIFI, isInternetValidated = false).isUsable,
        )
        // Une validation sans transport n'a pas de sens et ne rend rien utilisable.
        assertFalse(
            NetworkContext(NetworkTransport.NONE, isInternetValidated = true).isUsable,
        )
    }

    @Test
    fun airplaneModeIsIndependentOfTheTransport() {
        // Le Wi-Fi peut rester actif en mode avion : les deux informations
        // coexistent sans se contredire.
        val context =
            NetworkContext(
                transport = NetworkTransport.WIFI,
                isAirplaneModeOn = true,
                isInternetValidated = true,
                ssid = "Maison",
            )

        assertTrue(context.isAirplaneModeOn)
        assertTrue(context.isUsable)
    }

    @Test
    fun aSsidIsOnlyAcceptedOnAWifiTransport() {
        NetworkTransport.entries
            .filter { it != NetworkTransport.WIFI }
            .forEach { transport ->
                assertFailsWith<IllegalArgumentException>(
                    "Le transport $transport ne devrait pas accepter de SSID.",
                ) {
                    NetworkContext(transport = transport, ssid = "Maison")
                }
            }
    }

    @Test
    fun aBlankSsidIsRejectedRatherThanSilentlyAccepted() {
        // L'indisponibilité se représente par null ; une chaîne vide serait un
        // SSID valide du point de vue du type, et fausserait les comparaisons.
        listOf("", " ", "\t").forEach { blank ->
            assertFailsWith<IllegalArgumentException> {
                NetworkContext(transport = NetworkTransport.WIFI, ssid = blank)
            }
        }
    }

    @Test
    fun anUnavailableSsidIsRepresentedByNull() {
        val context = NetworkContext(transport = NetworkTransport.WIFI, ssid = null)

        assertNull(context.ssid)
        assertTrue(context.isConnected)
    }

    @Test
    fun copyRevalidatesTheInvariants() {
        val wifi = NetworkContext(transport = NetworkTransport.WIFI, ssid = "Maison")

        assertFailsWith<IllegalArgumentException> {
            wifi.copy(transport = NetworkTransport.CELLULAR)
        }
    }

    @Test
    fun twoIdenticalContextsAreEqual() {
        // L'égalité conditionne le distinctUntilChanged de l'observation réseau :
        // un contexte identique ne doit pas provoquer de nouvelle évaluation.
        val first = NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison")
        val second = NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun theDisconnectedConstantCarriesNoNetworkAtAll() {
        val context = NetworkContext.Disconnected

        assertEquals(NetworkTransport.NONE, context.transport)
        assertFalse(context.isConnected)
        assertFalse(context.isUsable)
        assertFalse(context.isAirplaneModeOn)
        assertNull(context.ssid)
    }
}
