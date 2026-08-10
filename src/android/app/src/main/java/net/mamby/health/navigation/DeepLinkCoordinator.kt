package net.mamby.health.navigation

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class DeepLinkKind {
    Dashboard,
    Medication,
    Schedule,
}

sealed interface DeepLinkTarget {
    data object Dashboard : DeepLinkTarget

    data class Medication(val profileId: String, val medicationId: String?) : DeepLinkTarget

    data class Schedule(val scheduleId: String?) : DeepLinkTarget
}

@Singleton
class DeepLinkCoordinator @Inject constructor() {
    private val targetChannel = Channel<DeepLinkTarget>(Channel.BUFFERED)

    val targets: Flow<DeepLinkTarget> = targetChannel.receiveAsFlow()

    fun accept(intent: Intent?) {
        val kind = intent?.getStringExtra(EXTRA_KIND)
            ?.let { raw -> DeepLinkKind.entries.firstOrNull { it.name == raw } }
            ?: return
        val target = when (kind) {
            DeepLinkKind.Dashboard -> DeepLinkTarget.Dashboard
            DeepLinkKind.Medication -> DeepLinkTarget.Medication(
                profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return,
                medicationId = intent.getStringExtra(EXTRA_RECORD_ID),
            )
            DeepLinkKind.Schedule -> DeepLinkTarget.Schedule(intent.getStringExtra(EXTRA_RECORD_ID))
        }
        targetChannel.trySend(target)
        intent.removeExtra(EXTRA_KIND)
        intent.removeExtra(EXTRA_PROFILE_ID)
        intent.removeExtra(EXTRA_RECORD_ID)
    }

    companion object {
        const val EXTRA_KIND = "net.mamby.health.extra.DEEP_LINK_KIND"
        const val EXTRA_PROFILE_ID = "net.mamby.health.extra.PROFILE_ID"
        const val EXTRA_RECORD_ID = "net.mamby.health.extra.RECORD_ID"
    }
}
