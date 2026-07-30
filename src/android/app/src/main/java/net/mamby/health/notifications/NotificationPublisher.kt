package net.mamby.health.notifications

enum class NotificationPermissionState {
    GRANTED,
    BLOCKED,
}

sealed interface NotificationPublishResult {
    data object Published : NotificationPublishResult

    data object PermissionBlocked : NotificationPublishResult
}

interface NotificationPublisher {
    fun permissionState(): NotificationPermissionState

    fun publish(request: ReminderRequest): NotificationPublishResult

    fun cancel(reminderId: String)

    fun cancelAll()
}
