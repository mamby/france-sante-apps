using Volta.Core.Config;
using Volta.Core.Data.Models;
using Volta.Core.L10n;

namespace Volta.Core.Data.Services;

public sealed class StaticAppDataService : IAppDataService
{
    private const string AppLockEnabledValue = "1";
    private const string AppLockDisabledValue = "0";

    private static readonly HomeSnapshot HomeData = new(
        Greeting: "Bonjour Marianne",
        Summary: "Your care data is synchronized and ready to share with providers.",
        Metrics:
        [
            new DashboardMetric("Unread alerts", "3", "2 require action"),
            new DashboardMetric("Upcoming visits", "2", "This week"),
            new DashboardMetric("Medication adherence", "94%", "Last 30 days"),
            new DashboardMetric("New lab results", "1", "Available today")
        ],
        UpcomingAppointments:
        [
            new AppointmentItem("Tue 10 Mar", "09:00", "Cardiology follow-up", "CHU Bordeaux", "Dr. Nathalie Roux"),
            new AppointmentItem("Fri 13 Mar", "15:30", "General practitioner", "Cabinet Volta", "Dr. Lucas Martin")
        ],
        MedicationReminders:
        [
            new MedicationReminder("Metformin", "500 mg", "Today at 20:00", "Take with dinner."),
            new MedicationReminder("Vitamin D", "1 capsule", "Tomorrow at 08:00", "Take after breakfast.")
        ]);

    private static readonly IReadOnlyList<HealthRecordSection> HealthRecordSections =
    [
        new HealthRecordSection("conditions", "Conditions", "Track ongoing diagnoses and active conditions.", 4, "Hypertension under control", "Updated 2 days ago"),
        new HealthRecordSection("allergies", "Allergies", "Record medication, food, and environmental allergies.", 3, "Penicillin listed as severe", "Updated 3 months ago"),
        new HealthRecordSection("medications", "Medications", "List current prescriptions, doses, and schedules.", 6, "2 reminders pending today", "Updated today"),
        new HealthRecordSection("vaccinations", "Vaccinations", "Keep immunization dates and booster reminders.", 8, "Influenza booster due in October", "Updated 4 weeks ago"),
        new HealthRecordSection("lab-results", "Lab results", "Store recent lab values and provider notes.", 14, "HbA1c improved to 6.8%", "Updated today"),
        new HealthRecordSection("imaging", "Imaging", "Track imaging summaries and follow-up needs.", 5, "MRI follow-up recommended in 6 months", "Updated 1 month ago"),
        new HealthRecordSection("procedures", "Procedures", "Document surgeries and significant procedures.", 2, "No pending post-op action", "Updated 6 months ago"),
        new HealthRecordSection("family-history", "Family history", "Capture hereditary conditions and risk factors.", 7, "Cardiovascular risk factors documented", "Updated 1 year ago"),
        new HealthRecordSection("emergency-contacts", "Emergency contacts", "Store trusted contacts and preferred hospitals.", 2, "Primary contact confirmed", "Updated 2 weeks ago"),
        new HealthRecordSection("doctors", "Doctors", "List primary care and specialist care teams.", 4, "Care team synced", "Updated today")
    ];

    private static readonly IReadOnlyList<NotificationItem> Notifications =
    [
        new NotificationItem("notif-001", "New lab result available", "Your blood panel from CHU Bordeaux is now available.", "Results", "Today at 08:42", true, true),
        new NotificationItem("notif-002", "Medication reminder", "Metformin 500 mg is scheduled for 20:00.", "Medication", "Today at 07:10", true, false),
        new NotificationItem("notif-003", "Appointment confirmation", "Cardiology follow-up confirmed for Tuesday, 10 March.", "Appointments", "Yesterday at 18:32", false, false),
        new NotificationItem("notif-004", "Vaccination reminder", "Influenza booster recommendation is available.", "Prevention", "Monday at 10:04", false, true)
    ];

    private static readonly UserProfile Profile = new(
        FullName: "Marianne Dupont",
        Email: "marianne.dupont@example.com",
        AvatarSource: "marianne.png",
        NationalHealthId: "NHI 2 84 12 75 123 456 78",
        DateOfBirthLabel: "14 Jul 1984",
        BloodType: "O+",
        Address: "18 Rue des Tilleuls, 33000 Bordeaux",
        EmergencyContacts:
        [
            new EmergencyContact("Paul Dupont", "Spouse", "+33 6 12 34 56 78", "CHU Bordeaux"),
            new EmergencyContact("Claire Moreau", "Sister", "+33 6 98 76 54 32", "Clinique Saint-Augustin")
        ],
        CareTeam:
        [
            new CareTeamMember("Dr. Lucas Martin", "General practitioner", "Cabinet Volta", "+33 5 56 11 22 33"),
            new CareTeamMember("Dr. Nathalie Roux", "Cardiologist", "CHU Bordeaux", "+33 5 56 44 55 66"),
            new CareTeamMember("Dr. Sarah Benali", "Endocrinologist", "Polyclinique Bordeaux Nord", "+33 5 56 77 88 99")
        ]);

    public Task<HomeSnapshot> GetHomeSnapshotAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(HomeData);
    }

    public Task<IReadOnlyList<HealthRecordSection>> GetHealthRecordSectionsAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(HealthRecordSections);
    }

    public Task<IReadOnlyList<NotificationItem>> GetNotificationsAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(Notifications);
    }

    public Task<UserProfile> GetUserProfileAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(Profile);
    }

    public async Task<MoreFeaturesSnapshot> GetMoreFeaturesSnapshotAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var settings = await GetSecuritySettingsAsync(cancellationToken);
        var lockStateText = settings.AppLockEnabled ? "Enabled" : "Disabled";

        var items = new List<MoreFeatureItem>
        {
            new("Language", "Interface language selection", settings.LanguageCode, true),
            new("Biometric lock", "Protect access with biometric verification", lockStateText, true),
            new("Auto-lock delay", "Delay before app requests unlock", $"{settings.LockAfterSeconds} seconds", true),
            new("Connected providers", "Data sharing with care providers", "3 linked organizations", true),
            new("Cloud backup", "Encrypted backup status", "Last backup today at 07:31", true)
        };

        return new MoreFeaturesSnapshot(items);
    }

    public async Task<AppSecuritySettings> GetSecuritySettingsAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var languageCode = Preferences.Get(SettingKeys.Language, SettingDefaults.Language);
        var lockAfterSeconds = Preferences.Get(SettingKeys.LockAfter, SettingDefaults.LockAfterSeconds);

        var appLockState = await ReadAppLockStateAsync();
        var appLockEnabled = appLockState == AppLockEnabledValue;

        return new AppSecuritySettings(appLockEnabled, lockAfterSeconds, languageCode);
    }

    public async Task SaveSecuritySettingsAsync(AppSecuritySettings settings, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        Preferences.Set(SettingKeys.Language, settings.LanguageCode);
        Preferences.Set(SettingKeys.LockAfter, settings.LockAfterSeconds);

        await WriteAppLockStateAsync(settings.AppLockEnabled ? AppLockEnabledValue : AppLockDisabledValue);
        Localization.SetLanguage(settings.LanguageCode);
    }

    public async Task SignOutAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        Preferences.Set(SettingKeys.LockAfter, SettingDefaults.LockAfterSeconds);
        await WriteAppLockStateAsync(AppLockDisabledValue);
    }

    private static async Task<string> ReadAppLockStateAsync()
    {
        try
        {
            var secureValue = await SecureStorage.Default.GetAsync(SettingKeys.AppLock);
            if (!string.IsNullOrWhiteSpace(secureValue))
            {
                return secureValue;
            }
        }
        catch
        {
            // Fallback to Preferences for platforms where SecureStorage is unavailable.
        }

        return Preferences.Get(SettingKeys.AppLock, SettingDefaults.AppLockState);
    }

    private static async Task WriteAppLockStateAsync(string value)
    {
        try
        {
            await SecureStorage.Default.SetAsync(SettingKeys.AppLock, value);
        }
        catch
        {
            // Fallback to Preferences for platforms where SecureStorage is unavailable.
        }

        Preferences.Set(SettingKeys.AppLock, value);
    }
}
