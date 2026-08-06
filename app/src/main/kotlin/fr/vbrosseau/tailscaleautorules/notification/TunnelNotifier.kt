package fr.vbrosseau.tailscaleautorules.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.MainActivity
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.presentation.labelRes
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publie et retire la notification d'état.
 *
 * **Elle est réellement optionnelle.** L'application n'emploie pas de service
 * de premier plan, lequel imposerait une notification permanente sur Android 8
 * et suivants et contredirait donc SPECS.md §7. La notification n'est donc pas
 * une contrainte de plateforme mais un choix de l'utilisateur.
 *
 * Toutes les opérations sont sans effet lorsque la permission de notification
 * n'a pas été accordée : c'est un cas nominal, pas une erreur, puisque
 * l'application ne la demande que si l'option est activée.
 */
@Singleton
class TunnelNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    /** Vrai lorsque l'utilisateur a accordé la permission requise. */
    fun canNotify(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Affiche l'état courant et la règle qui l'a produit.
     *
     * @param ruleId règle ayant décidé en dernier, ou `null` si aucune décision
     *   n'a encore été appliquée.
     */
    fun show(state: TunnelState, ruleId: RuleId?) {
        if (!canNotify()) {
            Timber.w("Notification non publiée : permission absente")
            return
        }

        NotificationChannels.ensureCreated(context)

        val notification = NotificationCompat.Builder(context, NotificationChannels.TUNNEL_STATE)
            .setSmallIcon(R.drawable.ic_notification_tunnel)
            .setContentTitle(context.getString(state.notificationTitleRes()))
            .setContentText(reasonText(ruleId))
            .setContentIntent(openApplicationIntent())
            // Persistante : elle décrit un état continu, pas un événement.
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
            Timber.i("Notification publiée : %s", state)
        } catch (ignored: SecurityException) {
            // La garde `canNotify()` ne suffit pas formellement : l'utilisateur
            // peut révoquer la permission entre le contrôle et l'appel. Perdre
            // une notification d'état est sans conséquence ; faire planter
            // l'application pour cela en aurait une.
        }
    }

    fun hide() {
        Timber.i("Notification retirée")
        manager.cancel(NOTIFICATION_ID)
    }

    private fun reasonText(ruleId: RuleId?): String = if (ruleId == null) {
        context.getString(R.string.notification_no_reason)
    } else {
        context.getString(R.string.notification_reason, context.getString(ruleId.labelRes()))
    }

    private fun openApplicationIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun TunnelState.notificationTitleRes(): Int = when (this) {
        TunnelState.ENABLED -> R.string.notification_tunnel_enabled
        TunnelState.DISABLED -> R.string.notification_tunnel_disabled
        TunnelState.UNKNOWN -> R.string.notification_tunnel_unknown
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
