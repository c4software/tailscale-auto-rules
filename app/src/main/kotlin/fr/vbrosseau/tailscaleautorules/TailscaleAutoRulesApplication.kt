package fr.vbrosseau.tailscaleautorules

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.vbrosseau.tailscaleautorules.automation.AutomationCoordinator
import fr.vbrosseau.tailscaleautorules.di.ApplicationScope
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Point d'entrée de l'application et racine du graphe d'injection.
 *
 * Elle ne porte aucune logique métier. Sa seule responsabilité propre est de
 * maintenir la plateforme alignée sur les préférences : armer ou désarmer le
 * réveil automatique, afficher ou retirer la notification. Sans cette
 * observation, une modification des paramètres ne prendrait effet qu'au
 * redémarrage suivant.
 */
@HiltAndroidApp
class TailscaleAutoRulesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager reçoit la fabrique de Hilt.
     *
     * Sans elle, il instancierait le worker par son constructeur sans argument
     * — inexistant ici — et le travail périodique échouerait à chaque
     * exécution, sans que rien ne le signale à l'utilisateur.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Processus démarré")

        scope.launch {
            settingsRepository.observeAppSettings()
                .distinctUntilChanged()
                .collect { settings ->
                    Timber.i("Réglages appliqués : %s", settings)
                    coordinator.applySettings(settings)
                }
        }
    }
}
