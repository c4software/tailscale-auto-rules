package fr.vbrosseau.tailscaleautorules.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Vérifie périodiquement que le tunnel est dans l'état voulu.
 *
 * C'est le mode économe : aucun processus permanent, aucune notification
 * imposée, mais une réaction différée. Android n'accepte pas de période plus
 * courte que quinze minutes pour un travail périodique — un changement de
 * réseau peut donc rester sans effet pendant ce laps de temps. C'est le
 * compromis explicite offert à l'utilisateur, l'alternative étant le service de
 * premier plan de [TunnelWatchService].
 *
 * Le travail est **persistant** : WorkManager le rejoue après un redémarrage du
 * terminal ou une mise à jour de l'application, sans intervention.
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coordinator: AutomationCoordinator,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        Timber.i("Vérification périodique")

        return runCatching { coordinator.synchronize() }
            .fold(
                onSuccess = {
                    Timber.i("Cycle terminé : %s", it)
                    Result.success()
                },
                onFailure = {
                    // Un échec ponctuel — client Tailscale en cours de
                    // démarrage, réseau instable — ne justifie pas d'abandonner :
                    // la prochaine exécution retentera de toute façon.
                    Timber.e(it, "Cycle en échec")
                    Result.retry()
                },
            )
    }

    companion object {
        private const val NAME = "periodic-sync"

        /**
         * Quinze minutes : le minimum imposé par la plateforme. Demander moins
         * ne raccourcit rien, WorkManager relève silencieusement la valeur.
         */
        private const val INTERVAL_MINUTES = 15L

        /**
         * `KEEP` plutôt que `UPDATE` : replanifier à chaque démarrage
         * repousserait indéfiniment la première exécution, et le travail ne
         * tournerait jamais chez un utilisateur qui ouvre souvent l'application.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
