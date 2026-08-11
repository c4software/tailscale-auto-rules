package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import kotlinx.coroutines.flow.first

/**
 * Constate un éventuel geste manuel sur l'état courant, et le mémorise.
 *
 * C'est la forme sans arguments qu'emploie l'observation continue quand le
 * tunnel bouge hors cycle : elle relit elle-même le réseau, l'état constaté et
 * le journal, puis enchaîne détection ([DetectManualOverrideUseCase]) et
 * mémorisation ([RecordManualOverrideUseCase]). Réseau et état sont lus une
 * seule fois : la détection et la mémorisation portent sur le même instant.
 *
 * Un client absent laisse l'état indéterminé, que la détection écarte
 * d'elle-même : rien n'est alors mémorisé.
 */
class CaptureManualOverrideUseCase(
    private val networkObserver: NetworkObserver,
    private val controller: TailscaleController,
    private val journalRepository: JournalRepository,
    private val detectManualOverride: DetectManualOverrideUseCase,
    private val recordManualOverride: RecordManualOverrideUseCase,
) {
    /** @return `true` lorsqu'un geste a été constaté **et** mémorisé. */
    suspend operator fun invoke(): Boolean {
        val networkContext = networkObserver.current()
        val tunnelState = observedTunnelState()
        val lastChange = journalRepository.observeRecent().first().firstOrNull()

        val manualOverride =
            detectManualOverride(networkContext, tunnelState, lastChange) ?: return false

        return recordManualOverride(networkContext, manualOverride)
    }

    private suspend fun observedTunnelState(): TunnelState =
        when {
            !controller.isAvailable() -> TunnelState.UNKNOWN
            controller.isRunning() -> TunnelState.ENABLED
            else -> TunnelState.DISABLED
        }
}
