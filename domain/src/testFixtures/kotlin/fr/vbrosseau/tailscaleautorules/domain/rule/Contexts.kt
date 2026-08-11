package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState

/**
 * Fabriques de contextes pour les tests.
 *
 * Elles nomment les situations de SPECS.md §4 plutôt que d'aligner des
 * booléens : un test se lit alors comme la spécification qu'il vérifie.
 */
object Contexts {
    /** Aucun réseau. */
    fun none(airplaneMode: Boolean = false) =
        RuleContext(
            network =
                NetworkContext(
                    transport = NetworkTransport.NONE,
                    isAirplaneModeOn = airplaneMode,
                ),
        )

    /** Wi-Fi associé et validé. [ssid] à `null` simule un SSID indisponible. */
    fun wifi(
        ssid: String? = "Réseau inconnu",
        validated: Boolean = true,
        airplaneMode: Boolean = false,
        blacklist: Set<String> = emptySet(),
        exceptions: Map<NetworkPreferenceKey, TunnelState> = emptyMap(),
    ) = RuleContext(
        network =
            NetworkContext(
                transport = NetworkTransport.WIFI,
                isAirplaneModeOn = airplaneMode,
                isInternetValidated = validated,
                ssid = ssid,
            ),
        blacklistedSsids = blacklist,
        networkPreferences = exceptions,
    )

    /** Réseau mobile. */
    fun cellular(
        validated: Boolean = true,
        airplaneMode: Boolean = false,
        exceptions: Map<NetworkPreferenceKey, TunnelState> = emptyMap(),
    ) = RuleContext(
        network =
            NetworkContext(
                transport = NetworkTransport.CELLULAR,
                isAirplaneModeOn = airplaneMode,
                isInternetValidated = validated,
            ),
        networkPreferences = exceptions,
    )

    /** Transport non couvert par les règles de la version 1. */
    fun other(
        transport: NetworkTransport,
        validated: Boolean = true,
    ) = RuleContext(
        network =
            NetworkContext(
                transport = transport,
                isInternetValidated = validated,
            ),
    )
}
