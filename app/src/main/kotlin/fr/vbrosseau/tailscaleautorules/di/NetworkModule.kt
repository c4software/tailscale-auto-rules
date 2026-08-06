package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.data.network.AndroidNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import javax.inject.Singleton

/** Relie le contrat d'observation du domaine à son implémentation Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindNetworkObserver(implementation: AndroidNetworkObserver): NetworkObserver
}
