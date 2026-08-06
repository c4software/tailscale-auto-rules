package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.automation.AutomationCoordinator
import fr.vbrosseau.tailscaleautorules.automation.AutomationTrigger
import fr.vbrosseau.tailscaleautorules.automation.NetworkCallbackTrigger
import fr.vbrosseau.tailscaleautorules.automation.NotificationRefresher
import fr.vbrosseau.tailscaleautorules.presentation.AndroidSystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
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
    abstract fun bindNotificationRefresher(
        implementation: AutomationCoordinator,
    ): NotificationRefresher

    @Binds
    @Singleton
    abstract fun bindSystemStatus(implementation: AndroidSystemStatus): SystemStatus
}
