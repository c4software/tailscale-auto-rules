package fr.vbrosseau.tailscaleautorules.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.automation.DisableAutomationReceiver
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TunnelNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: TunnelNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        grantNotificationPermission()
        notifier = TunnelNotifier(context)
    }

    private fun grantNotificationPermission() {
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun denyNotificationPermission() {
        Shadows.shadowOf(context as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun postedNotification() = Shadows.shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).getNotification(TunnelNotifier.NOTIFICATION_ID)

    @Test
    fun showingPostsAPersistentNotification() {
        notifier.show(TunnelState.ENABLED, RuleId("mobile-network"))

        val notification = assertNotNull(postedNotification())
        assertTrue(
            notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0,
            "La notification décrit un état continu, pas un événement.",
        )
    }

    private fun channel() = context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(NotificationChannels.TUNNEL_STATE)

    @Test
    fun theNotificationOffersToDisableTheAutomation() {
        // Persistante et non rejetable, elle doit offrir le moyen de faire
        // cesser ce qu'elle décrit — sans repasser par l'application.
        notifier.show(TunnelState.ENABLED, RuleId("mobile-network"))

        val action = assertNotNull(postedNotification()).actions.single()
        assertEquals(
            context.getString(R.string.notification_disable_automation),
            action.title.toString(),
        )

        val intent = Shadows.shadowOf(action.actionIntent).savedIntent
        assertEquals(DisableAutomationReceiver.ACTION_DISABLE_AUTOMATION, intent.action)
        assertEquals(
            DisableAutomationReceiver::class.java.name,
            assertNotNull(intent.component).className,
        )
    }

    @Test
    fun aManualOverrideReplacesTheRuleAsTheDisplayedReason() {
        // Attribuer à une règle un état qu'elle n'a pas produit serait un
        // mensonge : le texte doit dire que le geste vient de l'utilisateur.
        notifier.show(TunnelState.ENABLED, RuleId("blacklisted-wifi"), isManuallyOverridden = true)

        assertEquals(
            context.getString(R.string.notification_manual_override),
            assertNotNull(postedNotification()).extras.getString(android.app.Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun theChannelIsCreatedOnDemand() {
        notifier.show(TunnelState.ENABLED, null)

        assertNotNull(channel())
    }

    @Test
    fun theChannelStaysDiscreet() {
        // Une importance plus élevée émettrait un son à chaque changement de
        // réseau, ce qui rendrait la notification insupportable.
        NotificationChannels.ensureCreated(context)

        assertEquals(NotificationManager.IMPORTANCE_LOW, channel().importance)
    }

    @Test
    fun hidingRemovesTheNotification() {
        notifier.show(TunnelState.ENABLED, RuleId("mobile-network"))

        notifier.hide()

        assertNull(postedNotification())
    }

    @Test
    fun nothingIsPostedWithoutThePermission() {
        // L'application ne demande la permission que si l'option est activée :
        // son absence est un cas nominal, pas une erreur.
        denyNotificationPermission()

        notifier.show(TunnelState.ENABLED, RuleId("mobile-network"))

        assertNull(postedNotification())
        assertTrue(!notifier.canNotify())
    }

    @Test
    fun hidingIsHarmlessWhenNothingWasPosted() {
        notifier.hide()

        assertNull(postedNotification())
    }

    private fun postedReminder() = Shadows.shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).getNotification(TunnelNotifier.STARTUP_REMINDER_ID)

    @Test
    fun theStartupReminderIsAOneOffInvitation() {
        // Contrairement à la notification d'état, il décrit un geste à faire :
        // il doit se rejeter d'un glissement et disparaître une fois honoré.
        notifier.showStartupReminder()

        val reminder = assertNotNull(postedReminder())
        assertEquals(NotificationChannels.REMINDERS, reminder.channelId)
        assertTrue(
            reminder.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0,
            "Le rappel disparaît de lui-même une fois touché.",
        )
        assertTrue(
            reminder.flags and android.app.Notification.FLAG_ONGOING_EVENT == 0,
            "Le rappel n'est pas persistant.",
        )
    }

    @Test
    fun theRemindersChannelAlerts() {
        // Un rappel silencieux passerait inaperçu, précisément quand il est
        // le seul signe que l'automatisation ne tourne pas.
        NotificationChannels.ensureCreated(context)

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(NotificationChannels.REMINDERS)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun dismissingRemovesTheStartupReminder() {
        notifier.showStartupReminder()

        notifier.dismissStartupReminder()

        assertNull(postedReminder())
    }

    @Test
    fun noReminderIsPostedWithoutThePermission() {
        denyNotificationPermission()

        notifier.showStartupReminder()

        assertNull(postedReminder())
    }

    @Test
    fun everyTunnelStateHasATitle() {
        // Le `when` interne est exhaustif ; ce test garantit qu'aucun état ne
        // produit une notification muette.
        TunnelState.entries.forEach { state ->
            notifier.show(state, null)
            assertNotNull(postedNotification(), "État $state sans notification.")
            notifier.hide()
        }
    }
}
