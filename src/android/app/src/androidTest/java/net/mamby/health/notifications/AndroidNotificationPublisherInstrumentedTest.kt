package net.mamby.health.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun notificationBuiltForPublishingContainsOnlyGenericLocalizedText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val posted = AndroidNotificationPublisher(context).buildNotification(REQUEST)
        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val body = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals(context.getString(net.mamby.health.R.string.notification_generic_title), title)
        assertEquals(context.getString(net.mamby.health.R.string.notification_generic_body), body)
        assertFalse(title.contains(REQUEST.title))
        assertFalse(body.contains(REQUEST.message))
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
            profileId = "2f59f953-d6a2-4577-af28-3576c994094f",
            type = ReminderType.GENERAL,
            title = "Permission acceptance test",
            message = "This notification must not be posted.",
            recurrence = ReminderRecurrence.Once(Instant.parse("2026-07-30T09:00:00Z")),
        )
    }
}
