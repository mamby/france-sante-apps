package net.mamby.health.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInMeasurementType
import net.mamby.health.core.model.MeasurementReading
import net.mamby.health.core.model.MeasurementTypeRef
import net.mamby.health.core.model.MeasurementUnit
import net.mamby.health.core.model.MeasurementUnitRef
import net.mamby.health.core.model.ProfileRecord

@StringRes
fun BuiltInMeasurementType.labelResource(): Int = when (this) {
    BuiltInMeasurementType.WEIGHT -> R.string.measurement_weight
    BuiltInMeasurementType.HEIGHT -> R.string.measurement_height
    BuiltInMeasurementType.BLOOD_PRESSURE -> R.string.measurement_blood_pressure
    BuiltInMeasurementType.PULSE -> R.string.measurement_pulse
    BuiltInMeasurementType.TEMPERATURE -> R.string.measurement_temperature
    BuiltInMeasurementType.OXYGEN_SATURATION -> R.string.measurement_oxygen_saturation
    BuiltInMeasurementType.BLOOD_GLUCOSE -> R.string.measurement_blood_glucose
}

@Composable
fun MeasurementTypeRef.localizedLabel(record: ProfileRecord): String = when (this) {
    is MeasurementTypeRef.BuiltIn -> stringResource(type.labelResource())
    is MeasurementTypeRef.Custom -> record.customMeasurementTypes
        .firstOrNull { it.id == id }
        ?.name
        ?: stringResource(R.string.measurement_custom)
}

fun MeasurementUnit.symbol(): String = when (this) {
    MeasurementUnit.KILOGRAM -> "kg"
    MeasurementUnit.POUND -> "lb"
    MeasurementUnit.CENTIMETER -> "cm"
    MeasurementUnit.INCH -> "in"
    MeasurementUnit.CELSIUS -> "°C"
    MeasurementUnit.FAHRENHEIT -> "°F"
    MeasurementUnit.BEATS_PER_MINUTE -> "bpm"
    MeasurementUnit.PERCENT -> "%"
    MeasurementUnit.MILLIMETERS_OF_MERCURY -> "mmHg"
    MeasurementUnit.MILLIGRAMS_PER_DECILITER -> "mg/dL"
    MeasurementUnit.MILLIMOLES_PER_LITER -> "mmol/L"
}

@Composable
fun MeasurementReading.localizedValue(): String {
    val formatter = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
        maximumFractionDigits = 2
    }
    return when (this) {
        is MeasurementReading.Scalar -> {
            val symbol = when (val valueUnit = unit) {
                is MeasurementUnitRef.BuiltIn -> valueUnit.unit.symbol()
                is MeasurementUnitRef.Custom -> valueUnit.symbol
            }
            "${formatter.format(value)} $symbol"
        }
        is MeasurementReading.BloodPressure -> buildString {
            append(formatter.format(systolic))
            append('/')
            append(formatter.format(diastolic))
            append(' ')
            append(
                when (val pressureUnit = unit) {
                    is MeasurementUnitRef.BuiltIn -> pressureUnit.unit.symbol()
                    is MeasurementUnitRef.Custom -> pressureUnit.symbol
                },
            )
            pulseBeatsPerMinute?.let {
                append(" · ")
                append(formatter.format(it))
                append(" bpm")
            }
        }
    }
}
