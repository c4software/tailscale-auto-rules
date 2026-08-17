package fr.vbrosseau.tailscaleautorules.presentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suivi du premier plan par les activités elles-mêmes.
 *
 * L'importance du processus ne fait pas foi : pendant l'exécution d'un
 * receveur de diffusion, le système l'élève au niveau « premier plan », ce qui
 * faisait passer un démarrage au boot pour un usage à l'écran (constaté sur
 * appareil : la garde de l'armement laissait passer un service voué au rejet).
 * Une activité démarrée, elle, ne ment pas.
 *
 * Enregistré par l'Application à la création du processus.
 */
@Singleton
class ForegroundStateTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private val startedActivities = AtomicInteger(0)

    /** Vrai lorsqu'au moins une activité de l'application est visible. */
    val isInForeground: Boolean
        get() = startedActivities.get() > 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
