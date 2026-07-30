package net.mamby.health.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.mamby.health.data.DemoVaultProvider
import net.mamby.health.data.LocalizedDemoVaultProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoDataModule {
    @Binds
    @Singleton
    abstract fun bindDemoVaultProvider(
        implementation: LocalizedDemoVaultProvider,
    ): DemoVaultProvider
}
