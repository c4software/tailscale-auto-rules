package fr.vbrosseau.tailscaleautorules.di

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
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
