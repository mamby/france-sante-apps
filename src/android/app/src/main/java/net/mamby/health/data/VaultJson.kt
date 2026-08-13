@file:kotlinx.serialization.UseSerializers(
    net.mamby.health.core.model.UuidSerializer::class,
    net.mamby.health.core.model.InstantSerializer::class,
    net.mamby.health.core.model.LocalDateSerializer::class,
    net.mamby.health.core.model.LocalTimeSerializer::class,
    net.mamby.health.core.model.DayOfWeekSerializer::class,
)

package net.mamby.health.data

import java.nio.charset.StandardCharsets
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.requireValid

data class DecodedVault(
    val sourceVersion: Int,
    val vault: HealthVault,
)

object VaultCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun encode(vault: HealthVault): ByteArray =
        json.encodeToString(vault.requireValid()).toByteArray(StandardCharsets.UTF_8)

    fun decode(bytes: ByteArray): DecodedVault {
        val source = bytes.toString(StandardCharsets.UTF_8)
        val sourceVersion = json.parseToJsonElement(source)
            .jsonObject["version"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: throw IllegalArgumentException("Health data schema version is missing.")
        if (sourceVersion != HealthVault.CURRENT_VERSION) {
            throw UnsupportedVaultVersionException(sourceVersion)
        }
        val vault = json.decodeFromString<HealthVault>(source).requireValid()
        return DecodedVault(sourceVersion, vault)
    }
}
