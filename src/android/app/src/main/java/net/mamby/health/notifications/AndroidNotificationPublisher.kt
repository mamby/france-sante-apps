package net.mamby.health.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import net.mamby.health.R
import net.mamby.health.navigation.DeepLinkCoordinator
import net.mamby.health.navigation.DeepLinkKind

@Singleton
class AndroidNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPublisher {
    override fun permissionState(): NotificationPermissionState {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return if (runtimePermissionGranted && NotificationManagerCompat.from(context)
                .areNotificationsEnabled()
        ) {
            NotificationPermissionState.GRANTED
        } else {
            NotificationPermissionState.BLOCKED
        }
    }

    @SuppressLint("MissingPermission")
    override fun publish(request: ReminderRequest): NotificationPublishResult {
        if (permissionState() == NotificationPermissionState.BLOCKED) {
            return NotificationPublishResult.PermissionBlocked
        }
        // permissionState performs the runtime POST_NOTIFICATIONS check before this call.
        ensureChannel()
        val notification = buildNotification(request)
        NotificationManagerCompat.from(context).notify(notificationId(request.id), notification)
        return NotificationPublishResult.Published
    }

    internal fun buildNotification(request: ReminderRequest) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_generic_title))
            .setContentText(context.getString(R.string.notification_generic_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(request))
            .build()

    override fun cancel(reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    override fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(request: ReminderRequest): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        intent
            .setAction(ACTION_OPEN_REMINDER)
            .setData("phv://reminder/${Uri.encode(request.id)}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(
                DeepLinkCoordinator.EXTRA_KIND,
                when (request.type) {
                    ReminderType.MEDICATION -> DeepLinkKind.Medication
                    ReminderType.APPOINTMENT -> DeepLinkKind.Appointment
                    ReminderType.GENERAL -> DeepLinkKind.Reminder
                }.name,
            )
            .putExtra(DeepLinkCoordinator.EXTRA_PROFILE_ID, request.profileId)
            .putExtra(DeepLinkCoordinator.EXTRA_RECORD_ID, request.targetId)
        return PendingIntent.getActivity(
            context,
            notificationId(request.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(reminderId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(reminderId.encodeToByteArray())
        return ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES).int and Int.MAX_VALUE
    }

    companion object {
        const val CHANNEL_ID = "health_reminders"
        const val ACTION_OPEN_REMINDER = "net.mamby.health.action.OPEN_REMINDER"
    }
}
