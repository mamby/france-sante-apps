package net.mamby.health.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Singleton
import net.mamby.health.crypto.AesGcmVaultCipher
import net.mamby.health.crypto.AndroidKeystoreVaultKeyProvider
import net.mamby.health.crypto.VaultCipher
import net.mamby.health.crypto.VaultKeyProvider
import net.mamby.health.data.DefaultVaultRepository
import net.mamby.health.data.DocumentBlobStore
import net.mamby.health.data.EncryptedDocumentBlobStore
import net.mamby.health.data.EncryptedVaultStore
import net.mamby.health.data.UuidGenerator
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultStore

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreDataBindings {
    @Binds
    @Singleton
    abstract fun bindVaultCipher(implementation: AesGcmVaultCipher): VaultCipher

    @Binds
    @Singleton
    abstract fun bindVaultKeyProvider(
        implementation: AndroidKeystoreVaultKeyProvider,
    ): VaultKeyProvider

    @Binds
    @Singleton
    abstract fun bindVaultStore(implementation: EncryptedVaultStore): VaultStore

    @Binds
    @Singleton
    abstract fun bindDocumentBlobStore(
        implementation: EncryptedDocumentBlobStore,
    ): DocumentBlobStore

    @Binds
    @Singleton
    abstract fun bindVaultRepository(implementation: DefaultVaultRepository): VaultRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CoreDataProviders {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideUuidGenerator(): UuidGenerator = UuidGenerator(UUID::randomUUID)

}
