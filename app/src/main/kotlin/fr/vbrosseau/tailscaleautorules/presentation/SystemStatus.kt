package fr.vbrosseau.tailscaleautorules.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État de la plateforme dont dépend l'interface.
 *
 * Abstrait derrière une interface pour que les ViewModels restent testables
 * sans Android : ce qu'ils font de ces informations est de la logique de
 * présentation, la façon de les obtenir n'en est pas.
 *
 * Les trois autorisations qu'il expose se modifient **hors** de l'application ;
 * elles doivent donc être reconstatées au retour à l'écran.
 */
interface SystemStatus {

    /** Vrai lorsque l'utilisateur a accordé la permission de notification. */
    fun canNotify(): Boolean

    /**
     * Vrai lorsque l'application peut lire le SSID du réseau courant.
     *
     * Android conditionne cette lecture à une permission de localisation. Sans
     * elle, le système renvoie une valeur de repli, et les règles Wi-Fi ne
     * peuvent pas distinguer un réseau de confiance d'un autre.
     */
    fun canReadSsid(): Boolean

    /**
     * Vrai lorsque le SSID reste lisible sans que l'application soit ouverte.
     *
     * Une localisation « pendant l'utilisation » est une permission de premier
     * plan : Android refuse alors de démarrer depuis un redémarrage du
     * terminal un service de type « localisation ». Seule l'autorisation
     * « Toujours autoriser » lève cette restriction.
     */
    fun canReadSsidInBackground(): Boolean

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

    override fun canReadSsid(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    override fun canReadSsidInBackground(): Boolean =
        // Avant Android 10, la permission d'arrière-plan n'existe pas :
        // l'octroi de premier plan vaut pour tout moment.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    override val versionName: String
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
}
