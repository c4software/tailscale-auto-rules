package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.automation.AutomationTrigger
import fr.vbrosseau.tailscaleautorules.automation.NetworkCallbackTrigger
import fr.vbrosseau.tailscaleautorules.presentation.settings.AndroidSystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.settings.SystemStatus
import javax.inject.Singleton

/** Relie les abstractions de plateforme à leurs implémentations Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationModule {

    @Binds
    @Singleton
    abstract fun bindAutomationTrigger(implementation: NetworkCallbackTrigger): AutomationTrigger

    @Binds
    @Singleton
    abstract fun bindSystemStatus(implementation: AndroidSystemStatus): SystemStatus
}
