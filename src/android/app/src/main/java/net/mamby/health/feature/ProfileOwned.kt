package net.mamby.health.feature

import java.util.UUID
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.ProfileRecord

internal data class ProfileOwned<out T>(
    val record: ProfileRecord,
    val value: T,
) {
    val profile: HealthProfile
        get() = record.profile

    val profileId: UUID
        get() = profile.id
}

internal inline fun <T> Iterable<ProfileRecord>.ownedItems(
    items: (ProfileRecord) -> Iterable<T>,
): List<ProfileOwned<T>> = flatMap { record ->
    items(record).map { value -> ProfileOwned(record, value) }
}
