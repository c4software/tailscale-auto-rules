package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.data.tailscale.AndroidTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import javax.inject.Singleton

/** Relie le contrat du domaine à son implémentation Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TailscaleModule {

    @Binds
    @Singleton
    abstract fun bindTailscaleController(
        implementation: AndroidTailscaleController,
    ): TailscaleController
}
