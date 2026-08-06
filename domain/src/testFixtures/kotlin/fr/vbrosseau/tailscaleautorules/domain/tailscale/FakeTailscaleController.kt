package fr.vbrosseau.tailscaleautorules.domain.tailscale

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contrôleur en mémoire, inspectable, pour les tests.
 *
 * C'est un Fake et non un mock : il implémente réellement le contrat, échecs
 * compris. Les champs sont nommés sans préfixe `is` pour ne pas entrer en
 * collision, côté JVM, avec les accesseurs générés pour les fonctions du
 * contrat.
 *
 * @param running état initial du tunnel.
 * @param available présence simulée du client officiel.
 */
class FakeTailscaleController(
    running: Boolean = false,
    var available: Boolean = true,
) : TailscaleController {
    private val runningState = MutableStateFlow(running)

    /** Nombre d'appels à [enable], quel qu'en soit le résultat. */
    var enableCount: Int = 0
        private set

    /** Nombre d'appels à [disable], quel qu'en soit le résultat. */
    var disableCount: Int = 0
        private set

    /**
     * Erreur à renvoyer au prochain [enable] ou [disable], puis oubliée.
     *
     * Simule un échec de transmission sans rendre le client indisponible pour
     * autant : ce sont deux modes de défaillance distincts.
     */
    var nextFailure: Throwable? = null

    override suspend fun isAvailable(): Boolean = available

    override suspend fun isRunning(): Boolean = runningState.value

    override fun observeRunning(): Flow<Boolean> = runningState.asStateFlow()

    override suspend fun enable(): Result<Unit> {
        enableCount++
        return applyCommand(targetState = true)
    }

    override suspend fun disable(): Result<Unit> {
        disableCount++
        return applyCommand(targetState = false)
    }

    private fun applyCommand(targetState: Boolean): Result<Unit> {
        if (!available) return Result.failure(TailscaleUnavailableException())

        nextFailure?.let { failure ->
            nextFailure = null
            return Result.failure(failure)
        }

        runningState.value = targetState
        return Result.success(Unit)
    }
}
