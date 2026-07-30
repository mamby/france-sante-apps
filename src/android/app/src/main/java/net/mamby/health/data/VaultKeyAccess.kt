package net.mamby.health.data

import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import net.mamby.health.crypto.VaultKeyProvider

internal suspend fun VaultKeyProvider.requireVaultKey(): SecretKey = try {
    getOrCreateKey()
} catch (error: CancellationException) {
    throw error
} catch (error: GeneralSecurityException) {
    throw VaultKeyUnavailableException("The Android Keystore vault key is unavailable.", error)
} catch (error: SecurityException) {
    throw VaultKeyUnavailableException("The Android Keystore vault key cannot be accessed.", error)
}
