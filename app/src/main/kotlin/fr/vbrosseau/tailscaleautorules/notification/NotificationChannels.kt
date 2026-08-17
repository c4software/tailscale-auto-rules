package fr.vbrosseau.tailscaleautorules.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import fr.vbrosseau.tailscaleautorules.R

/**
 * Canal de la notification d'état.
 *
 * L'importance est basse et volontairement : cette notification informe, elle
 * n'alerte pas. Une importance plus élevée émettrait un son à chaque
 * changement de réseau, ce qui la rendrait vite insupportable.
 */
object NotificationChannels {
    const val TUNNEL_STATE = "tunnel-state"

    /**
     * Canal des rappels ponctuels, tel « ouvrir l'application pour démarrer ».
     *
     * Distinct du canal d'état, et d'importance normale : un rappel est un
     * événement qui attend un geste, pas un état qu'on consulte. Le fondre
     * dans le canal discret le ferait passer inaperçu, précisément quand il
     * est le seul signe que l'automatisation ne tourne pas.
     */
    const val REMINDERS = "reminders"

    fun ensureCreated(context: Context) {
        val tunnelState = NotificationChannel(
            TUNNEL_STATE,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_reminders_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_reminders_channel_description)
        }

        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(tunnelState)
            createNotificationChannel(reminders)
        }
    }
}
