package fr.vbrosseau.tailscaleautorules.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.NetworkExceptionRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import fr.vbrosseau.tailscaleautorules.domain.usecase.CaptureManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DescribeTunnelStatusUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.EvaluateRulesUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.RecordManualOverrideUseCase
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
    fun provideEvaluateRulesUseCase(
        blacklistRepository: BlacklistRepository,
        networkExceptionRepository: NetworkExceptionRepository,
        settingsRepository: SettingsRepository,
        engine: RuleEngine,
    ): EvaluateRulesUseCase = EvaluateRulesUseCase(
        blacklistRepository = blacklistRepository,
        networkExceptionRepository = networkExceptionRepository,
        settingsRepository = settingsRepository,
        engine = engine,
    )

    @Provides
    @Singleton
    fun provideSynchronizeTunnelUseCase(
        networkObserver: NetworkObserver,
        settingsRepository: SettingsRepository,
        evaluateRules: EvaluateRulesUseCase,
        controller: TailscaleController,
        journalRepository: JournalRepository,
    ): SynchronizeTunnelUseCase = SynchronizeTunnelUseCase(
        networkObserver = networkObserver,
        settingsRepository = settingsRepository,
        evaluateRules = evaluateRules,
        controller = controller,
        journalRepository = journalRepository,
    )

    @Provides
    @Singleton
    fun provideDetectManualOverrideUseCase(
        networkObserver: NetworkObserver,
        evaluateRules: EvaluateRulesUseCase,
        clock: Clock,
    ): DetectManualOverrideUseCase = DetectManualOverrideUseCase(
        networkObserver = networkObserver,
        evaluateRules = evaluateRules,
        clock = clock,
    )

    @Provides
    @Singleton
    fun provideRecordManualOverrideUseCase(
        settingsRepository: SettingsRepository,
        networkExceptionRepository: NetworkExceptionRepository,
        journalRepository: JournalRepository,
    ): RecordManualOverrideUseCase = RecordManualOverrideUseCase(
        settingsRepository = settingsRepository,
        exceptionRepository = networkExceptionRepository,
        journalRepository = journalRepository,
    )

    @Provides
    @Singleton
    fun provideCaptureManualOverrideUseCase(
        networkObserver: NetworkObserver,
        controller: TailscaleController,
        journalRepository: JournalRepository,
        detectManualOverride: DetectManualOverrideUseCase,
        recordManualOverride: RecordManualOverrideUseCase,
    ): CaptureManualOverrideUseCase = CaptureManualOverrideUseCase(
        networkObserver = networkObserver,
        controller = controller,
        journalRepository = journalRepository,
        detectManualOverride = detectManualOverride,
        recordManualOverride = recordManualOverride,
    )

    @Provides
    @Singleton
    fun provideDescribeTunnelStatusUseCase(
        networkObserver: NetworkObserver,
        evaluateRules: EvaluateRulesUseCase,
        detectManualOverride: DetectManualOverrideUseCase,
        journalRepository: JournalRepository,
        controller: TailscaleController,
    ): DescribeTunnelStatusUseCase = DescribeTunnelStatusUseCase(
        networkObserver = networkObserver,
        evaluateRules = evaluateRules,
        detectManualOverride = detectManualOverride,
        journalRepository = journalRepository,
        controller = controller,
    )
}
