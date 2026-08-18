package fr.vbrosseau.tailscaleautorules.notification

import android.Manifest
import android.app.Notification
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
import fr.vbrosseau.tailscaleautorules.automation.DisableAutomationReceiver
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.presentation.labelRes
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publie et retire la notification d'état.
 *
 * **Elle n'est optionnelle qu'en mode économe.** En mode immédiat, un service
 * de premier plan observe le réseau en continu, et Android impose alors une
 * notification permanente depuis la version 8 (SPECS.md §7).
 *
 * Toutes les opérations sont sans effet lorsque la permission de notification
 * n'a pas été accordée : c'est un cas nominal, pas une erreur. Le parcours de
 * premier lancement la demande systématiquement, mais l'utilisateur reste
 * libre de la refuser ou de la retirer ensuite.
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
     * @param isManuallyOverridden l'état constaté vient d'un geste de
     *   l'utilisateur, pas d'une règle : la raison affichée le dit, plutôt que
     *   d'attribuer à une règle un état qu'elle n'a pas produit.
     */
    fun show(state: TunnelState, ruleId: RuleId?, isManuallyOverridden: Boolean = false) {
        if (!canNotify()) {
            Timber.w("Notification non publiée : permission absente")
            return
        }

        val notification = build(state, ruleId, isManuallyOverridden)

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

    /**
     * Construit la notification sans la publier.
     *
     * Le service de premier plan doit remettre la sienne à `startForeground` :
     * il ne peut pas se contenter de la voir publiée, la plateforme exige de la
     * recevoir. C'est la même notification, décrite au même endroit.
     */
    fun build(state: TunnelState, ruleId: RuleId?, isManuallyOverridden: Boolean = false): Notification {
        NotificationChannels.ensureCreated(context)

        return NotificationCompat.Builder(context, NotificationChannels.TUNNEL_STATE)
            .setSmallIcon(R.drawable.ic_notification_tunnel)
            .setContentTitle(context.getString(state.notificationTitleRes()))
            .setContentText(
                if (isManuallyOverridden) {
                    context.getString(R.string.notification_manual_override)
                } else {
                    reasonText(ruleId)
                },
            )
            .setContentIntent(openApplicationIntent())
            // Persistante : elle décrit un état continu, pas un événement.
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Une notification qu'on ne peut pas retirer doit offrir le moyen
            // de faire cesser ce qu'elle décrit : couper l'automatisation,
            // sans ouvrir l'application.
            .addAction(
                0,
                context.getString(R.string.notification_disable_automation),
                disableAutomationIntent(),
            )
            .build()
    }

    fun hide() {
        Timber.i("Notification retirée")
        manager.cancel(NOTIFICATION_ID)
    }

    /**
     * Invite à ouvrir l'application quand le service ne peut pas partir seul.
     *
     * Au redémarrage du terminal, une localisation limitée à « pendant
     * l'utilisation » interdit de démarrer le service depuis l'arrière-plan
     * (SPECS.md §8) : ce rappel est alors le seul signe que l'automatisation
     * attend. Rejetable et à disparition automatique, car il décrit un geste
     * à faire, pas un état.
     */
    fun showStartupReminder() {
        if (!canNotify()) {
            Timber.w("Rappel de démarrage non publié : permission absente")
            return
        }

        NotificationChannels.ensureCreated(context)

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_tunnel)
            .setContentTitle(context.getString(R.string.notification_startup_reminder_title))
            .setContentText(context.getString(R.string.notification_startup_reminder_text))
            .setContentIntent(openApplicationIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            manager.notify(STARTUP_REMINDER_ID, notification)
            Timber.i("Rappel de démarrage publié")
        } catch (ignored: SecurityException) {
            // Même course qu'au-dessus : la permission peut tomber entre la
            // garde et l'appel, et perdre un rappel ne justifie pas un plantage.
        }
    }

    /** Retire le rappel : le service démarré, il n'a plus d'objet. */
    fun dismissStartupReminder() {
        manager.cancel(STARTUP_REMINDER_ID)
    }

    private fun reasonText(ruleId: RuleId?): String = if (ruleId == null) {
        context.getString(R.string.notification_no_reason)
    } else {
        context.getString(R.string.notification_reason, context.getString(ruleId.labelRes()))
    }

    private fun disableAutomationIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        DisableAutomationReceiver.intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
        const val STARTUP_REMINDER_ID = 2
    }
}
