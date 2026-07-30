package net.mamby.health.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.mamby.health.R
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.ReminderRecurrence

@StringRes
fun DocumentCategory.labelResource(): Int = when (this) {
    DocumentCategory.ALL -> R.string.category_all
    DocumentCategory.LAB_RESULTS -> R.string.category_lab_results
    DocumentCategory.PRESCRIPTIONS -> R.string.category_prescriptions
    DocumentCategory.REPORTS -> R.string.category_reports
    DocumentCategory.VACCINATIONS -> R.string.category_vaccinations
    DocumentCategory.OTHER -> R.string.category_other
}

@StringRes
fun ReminderRecurrence.labelResource(): Int = when (this) {
    ReminderRecurrence.NONE -> R.string.recurrence_once
    ReminderRecurrence.DAILY -> R.string.recurrence_daily
    ReminderRecurrence.WEEKLY -> R.string.recurrence_weekly
    ReminderRecurrence.MONTHLY -> R.string.recurrence_monthly
}

@Composable
fun DocumentCategory.localizedLabel(): String = stringResource(labelResource())

@Composable
fun ReminderRecurrence.localizedLabel(): String = stringResource(labelResource())

fun LocalDate.localizedDate(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun LocalTime.localizedTime(): String = format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun Instant.localizedDateTime(zoneId: ZoneId): String =
    atZone(zoneId).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
