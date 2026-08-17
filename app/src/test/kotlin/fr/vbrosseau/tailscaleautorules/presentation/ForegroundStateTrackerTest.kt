package fr.vbrosseau.tailscaleautorules.presentation

import android.app.Activity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ForegroundStateTrackerTest {

    private val tracker = ForegroundStateTracker()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun aFreshProcessIsInBackground() {
        // C'est l'état au boot : le processus vit pour un receveur, aucune
        // activité n'a démarré, et l'armement doit le savoir.
        assertTrue(!tracker.isInForeground)
    }

    @Test
    fun aStartedActivityPutsTheApplicationInForeground() {
        tracker.onActivityStarted(activity)

        assertTrue(tracker.isInForeground)
    }

    @Test
    fun stoppingTheLastActivityReturnsToBackground() {
        tracker.onActivityStarted(activity)

        tracker.onActivityStopped(activity)

        assertTrue(!tracker.isInForeground)
    }

    @Test
    fun aRecreationOverlapKeepsTheForeground() {
        // Pendant une rotation, la nouvelle activité démarre avant l'arrêt de
        // l'ancienne : le compte doit rester positif tout du long.
        tracker.onActivityStarted(activity)
        tracker.onActivityStarted(activity)

        tracker.onActivityStopped(activity)

        assertTrue(tracker.isInForeground)
    }
}
