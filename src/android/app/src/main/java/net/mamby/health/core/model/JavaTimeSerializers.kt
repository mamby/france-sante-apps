package net.mamby.health.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UuidSerializer : StringSerializer<UUID>("UUID", UUID::fromString, UUID::toString)

object InstantSerializer : StringSerializer<Instant>("Instant", Instant::parse, Instant::toString)

object LocalDateSerializer :
    StringSerializer<LocalDate>("LocalDate", LocalDate::parse, LocalDate::toString)

object LocalTimeSerializer :
    StringSerializer<LocalTime>("LocalTime", LocalTime::parse, LocalTime::toString)

object DayOfWeekSerializer : StringSerializer<DayOfWeek>(
    serialName = "DayOfWeek",
    decode = { DayOfWeek.valueOf(it) },
    encode = DayOfWeek::name,
)

abstract class StringSerializer<T>(
    serialName: String,
    private val decode: (String) -> T,
    private val encode: (T) -> String,
) : KSerializer<T> {
    final override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    final override fun deserialize(decoder: Decoder): T = decode(decoder.decodeString())

    final override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeString(encode(value))
}
