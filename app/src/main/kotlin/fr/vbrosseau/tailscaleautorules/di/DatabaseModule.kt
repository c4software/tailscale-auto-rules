package fr.vbrosseau.tailscaleautorules.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.tailscaleautorules.data.local.AppDatabase
import fr.vbrosseau.tailscaleautorules.data.local.BlacklistDao
import fr.vbrosseau.tailscaleautorules.data.local.JournalDao
import fr.vbrosseau.tailscaleautorules.data.local.NetworkPreferenceDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideBlacklistDao(database: AppDatabase): BlacklistDao = database.blacklistDao()

    @Provides
    fun provideJournalDao(database: AppDatabase): JournalDao = database.journalDao()

    @Provides
    fun provideNetworkPreferenceDao(database: AppDatabase): NetworkPreferenceDao =
        database.networkPreferenceDao()
}
