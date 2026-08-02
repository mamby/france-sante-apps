package net.mamby.health.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.ReminderRecurrence

@StringRes
fun BuiltInDocumentCategory.labelResource(): Int = when (this) {
    BuiltInDocumentCategory.LAB_RESULTS -> R.string.category_lab_results
    BuiltInDocumentCategory.PRESCRIPTIONS -> R.string.category_prescriptions
    BuiltInDocumentCategory.REPORTS -> R.string.category_reports
    BuiltInDocumentCategory.VACCINATIONS -> R.string.category_vaccinations
    BuiltInDocumentCategory.INVOICES_RECEIPTS -> R.string.category_invoices_receipts
    BuiltInDocumentCategory.DIRECTIVES -> R.string.category_directives
    BuiltInDocumentCategory.OTHER -> R.string.category_other
}

@StringRes
fun ReminderRecurrence.labelResource(): Int = when (this) {
    ReminderRecurrence.NONE -> R.string.recurrence_once
    ReminderRecurrence.DAILY -> R.string.recurrence_daily
    ReminderRecurrence.WEEKLY -> R.string.recurrence_weekly
    ReminderRecurrence.MONTHLY -> R.string.recurrence_monthly
}

@Composable
fun DocumentCategoryRef.localizedLabel(record: ProfileRecord): String = when (this) {
    is DocumentCategoryRef.BuiltIn -> record.builtInDocumentCategoryPreferences
        .firstOrNull { it.category == category }
        ?.labelOverride
        ?: stringResource(category.labelResource())
    is DocumentCategoryRef.Custom -> record.customDocumentCategories
        .firstOrNull { it.id == id }
        ?.name
        ?: stringResource(R.string.category_other)
}

@Composable
fun ReminderRecurrence.localizedLabel(): String = stringResource(labelResource())

@Composable
fun LocalDate.localizedDate(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

@Composable
fun LocalTime.localizedTime(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

@Composable
fun Iterable<LocalTime>.localizedTimes(): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    return joinToString { it.format(formatter) }
}

@Composable
fun Instant.localizedDateTime(zoneId: ZoneId): String {
    val locale = LocalConfiguration.current.locales[0]
    return atZone(zoneId).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale),
    )
}
