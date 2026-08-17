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
        override fun nowEpochMillis(): Long = System.currentTimeMillis()

        // `elapsedRealtime` court depuis le boot, veille profonde comprise :
        // la soustraction redonne l'instant du démarrage en temps d'époque.
        override fun bootEpochMillis(): Long =
            System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }
}
