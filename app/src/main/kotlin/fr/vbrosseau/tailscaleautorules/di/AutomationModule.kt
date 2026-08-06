package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.automation.AutomationTrigger
import fr.vbrosseau.tailscaleautorules.automation.NetworkCallbackTrigger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationModule {
    @Binds
    @Singleton
    abstract fun bindAutomationTrigger(implementation: NetworkCallbackTrigger): AutomationTrigger
}
