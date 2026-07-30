package net.mamby.health.data

import java.nio.charset.StandardCharsets
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamby.health.core.model.HealthVault

internal object VaultJson {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun encode(vault: HealthVault): ByteArray =
        json.encodeToString(vault).toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): HealthVault =
        json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))
}
