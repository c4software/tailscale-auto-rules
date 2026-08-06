package fr.vbrosseau.tailscaleautorules.domain.network

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import kotlinx.coroutines.flow.Flow

/**
 * Source des contextes réseau, vue du domaine.
 *
 * Deux accès distincts, pour deux besoins distincts :
 *
 * - [observe] alimente la synchronisation automatique. Le flux est **stabilisé**
 *   (voir [stabilized]) : les rafales d'événements d'une même transition
 *   réseau n'y produisent qu'une seule valeur.
 * - [current] sert la synchronisation manuelle, qui ne doit jamais être
 *   retardée par le debounce.
 */
interface NetworkObserver {
    /** Flux stabilisé des contextes réseau. */
    fun observe(): Flow<NetworkContext>

    /** Contexte réseau au moment de l'appel, sans attente. */
    suspend fun current(): NetworkContext
}
