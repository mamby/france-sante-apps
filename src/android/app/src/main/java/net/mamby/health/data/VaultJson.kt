package net.mamby.health.data

import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.InstantSerializer
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.Vaccination
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
        val version = json.parseToJsonElement(source)
            .jsonObject["version"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: throw IllegalArgumentException("Vault schema version is missing.")
        val vault = when (version) {
            1 -> json.decodeFromString<HealthVaultV1>(source).toCurrent()
            HealthVault.CURRENT_VERSION -> json.decodeFromString<HealthVault>(source)
            else -> throw UnsupportedVaultVersionException(version)
        }
        return DecodedVault(version, vault)
    }
}

@Serializable
private data class HealthVaultV1(
    val version: Int = 1,
    val revision: Long,
    val profile: HealthProfile,
    val documents: List<MedicalDocument> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
) {
    fun toCurrent(): HealthVault = HealthVault(
        revision = revision,
        profiles = listOf(
            ProfileRecord(
                profile = profile,
                documents = documents,
                medications = medications,
                appointments = appointments,
                vaccinations = vaccinations,
                reminders = reminders,
            ),
        ),
        updatedAt = updatedAt,
    )
}
