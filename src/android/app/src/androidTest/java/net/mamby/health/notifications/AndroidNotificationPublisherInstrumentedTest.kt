package net.mamby.health.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RunWith(AndroidJUnit4::class)
class AndroidNotificationPublisherInstrumentedTest {
    @Test
    fun deniedRuntimePermission_blocksPublishingBeforeNotificationManagerMutation() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val notificationsBefore = notificationManager.activeNotifications.map { it.id to it.tag }.toSet()
        val channelBefore = notificationManager.getNotificationChannel(AndroidNotificationPublisher.CHANNEL_ID)
        val publisher = AndroidNotificationPublisher(DeniedNotificationContext(applicationContext))

        val permissionState = publisher.permissionState()
        val publishResult = publisher.publish(REQUEST)

        assertEquals(NotificationPermissionState.BLOCKED, permissionState)
        assertEquals(NotificationPublishResult.PermissionBlocked, publishResult)
        assertEquals(channelBefore, notificationManager.getNotificationChannel(AndroidNotificationPublisher.CHANNEL_ID))
        assertEquals(notificationsBefore, notificationManager.activeNotifications.map { it.id to it.tag }.toSet())
    }

    private class DeniedNotificationContext(base: Context) : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                PackageManager.PERMISSION_DENIED
            } else {
                super.checkPermission(permission, pid, uid)
            }
    }

    private companion object {
        val REQUEST = ReminderRequest(
            id = "permission-test:${Instant.parse("2026-07-30T08:00:00Z")}",
            type = ReminderType.GENERAL,
            title = "Permission acceptance test",
            message = "This notification must not be posted.",
            recurrence = ReminderRecurrence.Once(Instant.parse("2026-07-30T09:00:00Z")),
        )
    }
}
