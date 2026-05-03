namespace Volta.Core.Data.Models;

public sealed record DashboardMetric(
    string Label,
    string Value,
    string Hint);

public sealed record AppointmentItem(
    string DayLabel,
    string TimeLabel,
    string Title,
    string Location,
    string Practitioner);

public sealed record MedicationReminder(
    string Medication,
    string Dose,
    string NextIntake,
    string Instruction);

public sealed record HomeSnapshot(
    string Greeting,
    string Summary,
    IReadOnlyList<DashboardMetric> Metrics,
    IReadOnlyList<AppointmentItem> UpcomingAppointments,
    IReadOnlyList<MedicationReminder> MedicationReminders);

public sealed record HealthRecordSection(
    string Key,
    string Title,
    string Description,
    int RecordCount,
    string Highlight,
    string LastUpdatedLabel);

public sealed record NotificationItem(
    string Id,
    string Title,
    string Message,
    string Category,
    string TimestampLabel,
    bool IsUnread,
    bool RequiresAction);

public sealed record EmergencyContact(
    string Name,
    string Relationship,
    string Phone,
    string PreferredHospital);

public sealed record CareTeamMember(
    string Name,
    string Specialty,
    string Facility,
    string Phone);

public sealed record UserProfile(
    string FullName,
    string Email,
    string AvatarSource,
    string NationalHealthId,
    string DateOfBirthLabel,
    string BloodType,
    string Address,
    IReadOnlyList<EmergencyContact> EmergencyContacts,
    IReadOnlyList<CareTeamMember> CareTeam);

public sealed record MoreFeatureItem(
    string Title,
    string Description,
    string Value,
    bool IsEnabled);

public sealed record MoreFeaturesSnapshot(
    IReadOnlyList<MoreFeatureItem> Items);

public sealed record AppSecuritySettings(
    bool AppLockEnabled,
    int LockAfterSeconds,
    string LanguageCode);
