package fr.vbrosseau.tailscaleautorules.domain.network

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observateur pilotable, pour les tests.
 *
 * Le flux n'est **pas** stabilisé : un test qui veut vérifier le debounce
 * applique [stabilized] lui-même, et garde ainsi la main sur la fenêtre.
 * Émettre déjà stabilisé rendrait ces tests impossibles à écrire.
 */
class FakeNetworkObserver(
    initialContext: NetworkContext = NetworkContext.Disconnected,
) : NetworkObserver {
    private val contexts = MutableStateFlow(initialContext)

    /** Nombre d'appels à [current], pour vérifier qu'une synchronisation manuelle a bien lieu. */
    var currentCount: Int = 0
        private set

    override fun observe(): Flow<NetworkContext> = contexts.asStateFlow()

    override suspend fun current(): NetworkContext {
        currentCount++
        return contexts.value
    }

    /** Simule une transition réseau. */
    fun emit(context: NetworkContext) {
        contexts.value = context
    }
}
