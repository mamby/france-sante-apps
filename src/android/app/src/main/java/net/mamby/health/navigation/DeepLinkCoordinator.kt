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
    Appointment,
    Reminder,
}

data class DeepLinkTarget(
    val kind: DeepLinkKind,
    val recordId: String? = null,
)

@Singleton
class DeepLinkCoordinator @Inject constructor() {
    private val targetChannel = Channel<DeepLinkTarget>(Channel.BUFFERED)

    val targets: Flow<DeepLinkTarget> = targetChannel.receiveAsFlow()

    fun accept(intent: Intent?) {
        val kind = intent?.getStringExtra(EXTRA_KIND)
            ?.let { raw -> DeepLinkKind.entries.firstOrNull { it.name == raw } }
            ?: return
        targetChannel.trySend(DeepLinkTarget(kind, intent.getStringExtra(EXTRA_RECORD_ID)))
        intent.removeExtra(EXTRA_KIND)
        intent.removeExtra(EXTRA_RECORD_ID)
    }

    companion object {
        const val EXTRA_KIND = "net.mamby.health.extra.DEEP_LINK_KIND"
        const val EXTRA_RECORD_ID = "net.mamby.health.extra.RECORD_ID"
    }
}
