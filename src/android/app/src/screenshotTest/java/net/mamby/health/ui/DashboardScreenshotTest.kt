package net.mamby.health.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.MedicationSchedule
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.ReminderRecurrence
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.ui.theme.HealthVaultTheme

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "Compact short", widthDp = 400, heightDp = 400)
@Preview(name = "Compact medium", widthDp = 400, heightDp = 500)
@Preview(name = "Compact tall", widthDp = 400, heightDp = 1_000)
@Preview(name = "Medium short", widthDp = 610, heightDp = 400)
@Preview(name = "Medium", widthDp = 610, heightDp = 500)
@Preview(name = "Medium tall", widthDp = 610, heightDp = 1_000)
@Preview(name = "Expanded short", widthDp = 900, heightDp = 400)
@Preview(name = "Expanded", widthDp = 900, heightDp = 500)
@Preview(name = "Expanded tall", widthDp = 900, heightDp = 1_000)
annotation class AdaptiveDashboardMatrix

@PreviewTest
@AdaptiveDashboardMatrix
@Composable
fun dashboardAdaptiveMatrix() {
    DashboardPreviewContent()
}

@PreviewTest
@Preview(
    name = "Compact dark",
    widthDp = 400,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun dashboardCompactDark() {
    DashboardPreviewContent(darkTheme = true)
}

@PreviewTest
@Preview(name = "Large text", widthDp = 400, heightDp = 1_000, fontScale = 1.5f)
@Composable
fun dashboardLargeText() {
    DashboardPreviewContent()
}

@PreviewTest
@Preview(name = "Arabic RTL", widthDp = 400, heightDp = 1_000, locale = "ar")
@Composable
fun dashboardArabicRtl() {
    DashboardPreviewContent()
}

@Composable
private fun DashboardPreviewContent(darkTheme: Boolean = false) {
    HealthVaultTheme(darkTheme = darkTheme) {
        DashboardScreen(
            vault = screenshotVault(),
            isDemo = true,
            clock = FIXED_CLOCK,
            zoneId = ZoneOffset.UTC,
            onStartVault = {},
            onSettings = {},
            onReminders = {},
            onDocumentSelected = {},
        )
    }
}

private fun screenshotVault(): HealthVault = HealthVault(
    revision = 3,
    profile = HealthProfile(
        id = id("11111111-1111-4111-8111-111111111111"),
        displayName = "Alex",
        bloodType = "O+",
        allergies = listOf("Penicillin"),
        chronicConditions = listOf("Asthma"),
        lastUpdatedAt = FIXED_INSTANT,
    ),
    documents = listOf(
        MedicalDocument(
            id = id("22222222-2222-4222-8222-222222222222"),
            title = "Blood test results",
            category = DocumentCategory.LAB_RESULTS,
            documentDate = LocalDate.of(2026, 7, 28),
            source = "Community clinic",
            blobId = id("33333333-3333-4333-8333-333333333333"),
            mimeType = "application/pdf",
            sizeBytes = 4_096,
            updatedAt = FIXED_INSTANT,
        ),
    ),
    medications = listOf(
        Medication(
            id = id("44444444-4444-4444-8444-444444444444"),
            name = "Daily medication",
            dose = "10 mg",
            instructions = "Take with water",
            schedule = MedicationSchedule(
                recurrence = ReminderRecurrence.DAILY,
                reminderTimes = listOf(LocalTime.of(8, 0)),
                startsOn = LocalDate.of(2026, 7, 1),
            ),
            remindersEnabled = true,
            updatedAt = FIXED_INSTANT,
        ),
    ),
    appointments = listOf(
        Appointment(
            id = id("55555555-5555-4555-8555-555555555555"),
            title = "Follow-up appointment",
            clinician = "Dr. Martin",
            location = "Community clinic",
            startsAt = Instant.parse("2026-08-02T09:30:00Z"),
            reminderLeadMinutes = 1_440,
            updatedAt = FIXED_INSTANT,
        ),
    ),
    reminders = listOf(
        Reminder(
            id = id("66666666-6666-4666-8666-666666666666"),
            title = "Renew prescription",
            startsOn = LocalDate.of(2026, 8, 1),
            timeOfDay = LocalTime.of(10, 0),
            updatedAt = FIXED_INSTANT,
        ),
    ),
    updatedAt = FIXED_INSTANT,
)

private fun id(value: String): UUID = UUID.fromString(value)

private val FIXED_INSTANT: Instant = Instant.parse("2026-07-30T08:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
