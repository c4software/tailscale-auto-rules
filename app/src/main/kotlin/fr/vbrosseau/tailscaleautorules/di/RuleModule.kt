package fr.vbrosseau.tailscaleautorules.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.rule.AirplaneModeRule
import fr.vbrosseau.tailscaleautorules.domain.rule.BlacklistedWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkExceptionRule
import fr.vbrosseau.tailscaleautorules.domain.rule.OtherWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.Rule
import javax.inject.Singleton

/**
 * Recense les règles actives de l'application.
 *
 * **C'est le seul fichier à modifier pour ajouter une règle.** Le moteur reçoit
 * un `Set<Rule>` et ignore totalement ce qu'il contient.
 *
 * Les règles sont construites ici plutôt qu'annotées `@Inject` : `:domain`
 * reste ainsi exempt de toute annotation d'injection, y compris `javax.inject`.
 */
@Module
@InstallIn(SingletonComponent::class)
object RuleModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideAirplaneModeRule(): Rule = AirplaneModeRule()

    @Provides
    @Singleton
    @IntoSet
    fun provideNetworkExceptionRule(): Rule = NetworkExceptionRule()

    @Provides
    @Singleton
    @IntoSet
    fun provideBlacklistedWifiRule(): Rule = BlacklistedWifiRule()

    @Provides
    @Singleton
    @IntoSet
    fun provideOtherWifiRule(): Rule = OtherWifiRule()

    @Provides
    @Singleton
    @IntoSet
    fun provideMobileNetworkRule(): Rule = MobileNetworkRule()

    @Provides
    @Singleton
    fun provideRuleEngine(rules: Set<@JvmSuppressWildcards Rule>): RuleEngine = RuleEngine(rules)
}
