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

    fun ensureCreated(context: Context) {
        val channel = NotificationChannel(
            TUNNEL_STATE,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
