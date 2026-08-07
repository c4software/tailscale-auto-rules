package fr.vbrosseau.tailscaleautorules.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import javax.inject.Singleton

/**
 * Assemble les cas d'usage du domaine.
 *
 * Ils sont construits ici plutôt qu'annotés `@Inject` : `:domain` reste exempt
 * de toute annotation d'injection, `javax.inject` comprise.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideSynchronizeTunnelUseCase(
        networkObserver: NetworkObserver,
        blacklistRepository: BlacklistRepository,
        settingsRepository: SettingsRepository,
        engine: RuleEngine,
        controller: TailscaleController,
        journalRepository: JournalRepository,
    ): SynchronizeTunnelUseCase = SynchronizeTunnelUseCase(
        networkObserver = networkObserver,
        blacklistRepository = blacklistRepository,
        settingsRepository = settingsRepository,
        engine = engine,
        controller = controller,
        journalRepository = journalRepository,
    )

    @Provides
    @Singleton
    fun provideDetectManualOverrideUseCase(
        networkObserver: NetworkObserver,
        blacklistRepository: BlacklistRepository,
        settingsRepository: SettingsRepository,
        engine: RuleEngine,
    ): DetectManualOverrideUseCase = DetectManualOverrideUseCase(
        networkObserver = networkObserver,
        blacklistRepository = blacklistRepository,
        settingsRepository = settingsRepository,
        engine = engine,
    )
}
