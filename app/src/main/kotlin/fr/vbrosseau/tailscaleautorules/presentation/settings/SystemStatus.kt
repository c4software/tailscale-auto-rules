package fr.vbrosseau.tailscaleautorules.presentation.settings

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État de la plateforme dont dépend l'écran des paramètres.
 *
 * Abstrait derrière une interface pour que le ViewModel reste testable sans
 * Android : ce qu'il fait de ces informations est de la logique de
 * présentation, la façon de les obtenir n'en est pas.
 */
interface SystemStatus {

    /** Vrai lorsque l'utilisateur a accordé la permission de notification. */
    fun canNotify(): Boolean

    /**
     * Vrai lorsque l'application est exemptée des restrictions de batterie.
     *
     * Sans exemption, Android peut différer les réveils de plusieurs minutes,
     * ce qui rendrait l'automatisation imprévisible.
     */
    fun isIgnoringBatteryOptimizations(): Boolean

    /** Version affichable de l'application. */
    val versionName: String
}

@Singleton
class AndroidSystemStatus @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: TunnelNotifier,
) : SystemStatus {

    override fun canNotify(): Boolean = notifier.canNotify()

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    override val versionName: String
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
}
