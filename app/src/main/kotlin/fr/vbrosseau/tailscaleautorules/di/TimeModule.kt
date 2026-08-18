package fr.vbrosseau.tailscaleautorules.di

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import javax.inject.Singleton

/** Seul endroit du projet appelant `System.currentTimeMillis()`. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = object : Clock {
        // `elapsedRealtime` court depuis le boot, veille profonde comprise :
        // la soustraction redonne l'instant du démarrage en temps d'époque.
        // Figé à la création du singleton : recalculé à chaque appel, le
        // repère dérivait avec les corrections d'horloge (NTP après un boot à
        // l'heure fausse), et pouvait alors rejeter les attestations de la
        // session, ou réadmettre celles d'avant le boot.
        private val bootMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        override fun nowEpochMillis(): Long = System.currentTimeMillis()

        override fun bootEpochMillis(): Long = bootMillis
    }
}
