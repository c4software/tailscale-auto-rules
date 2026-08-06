package fr.vbrosseau.tailscaleautorules.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private const val SETTINGS_FILE = "settings"

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * La portée est construite sur le dispatcher injecté plutôt que sur
     * `Dispatchers.IO` : c'est ce qui permet à un test de piloter les écritures.
     * `SupervisorJob` évite qu'une écriture en échec annule les suivantes.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
    )
}
