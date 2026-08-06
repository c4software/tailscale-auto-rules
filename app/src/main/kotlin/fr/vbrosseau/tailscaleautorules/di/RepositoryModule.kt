package fr.vbrosseau.tailscaleautorules.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.data.repository.DataStoreSettingsRepository
import fr.vbrosseau.tailscaleautorules.data.repository.RoomBlacklistRepository
import fr.vbrosseau.tailscaleautorules.data.repository.RoomJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import javax.inject.Singleton

/** Relie les contrats du domaine à leurs implémentations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlacklistRepository(
        implementation: RoomBlacklistRepository,
    ): BlacklistRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(implementation: RoomJournalRepository): JournalRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: DataStoreSettingsRepository,
    ): SettingsRepository
}
