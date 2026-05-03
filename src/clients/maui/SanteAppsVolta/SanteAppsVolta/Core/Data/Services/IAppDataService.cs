using Volta.Core.Data.Models;

namespace Volta.Core.Data.Services;

public interface IAppDataService
{
    Task<HomeSnapshot> GetHomeSnapshotAsync(CancellationToken cancellationToken = default);

    Task<IReadOnlyList<HealthRecordSection>> GetHealthRecordSectionsAsync(CancellationToken cancellationToken = default);

    Task<IReadOnlyList<NotificationItem>> GetNotificationsAsync(CancellationToken cancellationToken = default);

    Task<UserProfile> GetUserProfileAsync(CancellationToken cancellationToken = default);

    Task<MoreFeaturesSnapshot> GetMoreFeaturesSnapshotAsync(CancellationToken cancellationToken = default);

    Task<AppSecuritySettings> GetSecuritySettingsAsync(CancellationToken cancellationToken = default);

    Task SaveSecuritySettingsAsync(AppSecuritySettings settings, CancellationToken cancellationToken = default);

    Task SignOutAsync(CancellationToken cancellationToken = default);
}
