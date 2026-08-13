package net.mamby.health.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.mamby.health.R

sealed interface AppRoute : NavKey

sealed interface TopLevelRoute : AppRoute

sealed interface EditorRoute : AppRoute {
    val sessionId: String
}

@Serializable data object HomeRoute : TopLevelRoute
@Serializable data object SearchRoute : TopLevelRoute
@Serializable data object HealthRecordsRoute : TopLevelRoute
@Serializable data object NotesRoute : TopLevelRoute
@Serializable data object MedicationsRoute : TopLevelRoute
@Serializable data object ScheduleRoute : TopLevelRoute
@Serializable data object ContactsRoute : TopLevelRoute

@Serializable data class DocumentDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class MedicationDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class ScheduleDetailRoute(val id: String) : AppRoute
@Serializable data class HealthInfoRoute(val profileId: String) : AppRoute
@Serializable data object DocumentsRoute : AppRoute
@Serializable data object MeasurementsRoute : AppRoute
@Serializable data class ManageDocumentCategoriesRoute(val profileId: String) : AppRoute
@Serializable data class ManageMeasurementTypesRoute(val profileId: String) : AppRoute
@Serializable data class EmergencyContactDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class VaccinationDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class NoteDetailRoute(val id: String) : AppRoute
@Serializable data class MeasurementDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class ContactDetailRoute(val id: String) : AppRoute
@Serializable data class FamilyHistoryDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class CareDirectiveDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class HealthIdentifierDetailRoute(val profileId: String, val id: String) : AppRoute

@Serializable
data class ContactEditorRoute(
    override val sessionId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class MedicationEditorRoute(
    override val sessionId: String,
    val profileId: String? = null,
    val id: String? = null,
) : EditorRoute

@Serializable
data class MeasurementEditorRoute(
    override val sessionId: String,
    val profileId: String? = null,
    val id: String? = null,
) : EditorRoute

@Serializable
data class ScheduleEditorRoute(
    override val sessionId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class HealthNoteEditorRoute(
    override val sessionId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class DocumentImportEditorRoute(
    override val sessionId: String,
    val profileId: String? = null,
) : EditorRoute

@Serializable
data class DocumentEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String,
) : EditorRoute

@Serializable
data class HealthProfileEditorRoute(
    override val sessionId: String,
    val profileId: String? = null,
) : EditorRoute

@Serializable
data class EmergencyContactEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class VaccinationEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class FamilyHistoryEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class CareDirectiveEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String? = null,
) : EditorRoute

@Serializable
data class HealthIdentifierEditorRoute(
    override val sessionId: String,
    val profileId: String,
    val id: String? = null,
) : EditorRoute

@Serializable data object SettingsRoute : TopLevelRoute
@Serializable data object ManageProfilesRoute : TopLevelRoute

enum class TopLevelDestination(
    val route: TopLevelRoute,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home(HomeRoute, R.string.nav_home, R.drawable.ic_lucide_house),
    Search(SearchRoute, R.string.nav_search, R.drawable.ic_lucide_search),
    HealthRecords(
        HealthRecordsRoute,
        R.string.nav_health_records,
        R.drawable.ic_lucide_heart_pulse,
    ),
    Notes(
        NotesRoute,
        R.string.nav_notes,
        R.drawable.ic_lucide_sticky_notes,
    ),
    Medications(
        MedicationsRoute,
        R.string.nav_medications,
        R.drawable.ic_lucide_pill,
    ),
    Schedule(
        ScheduleRoute,
        R.string.schedule_title,
        R.drawable.ic_lucide_calendar_days,
    ),
    Contacts(
        ContactsRoute,
        R.string.contacts_title,
        R.drawable.ic_lucide_list,
    ),
    Settings(
        SettingsRoute,
        R.string.settings_title,
        R.drawable.ic_lucide_settings,
    ),
    Profiles(ManageProfilesRoute, R.string.profiles_title, R.drawable.ic_lucide_users),
    ;

    companion object {
        val compactPrimary = listOf(Home, Search, HealthRecords, Notes)
        val compactOverflow = listOf(Medications, Schedule, Contacts, Settings, Profiles)
    }
}
