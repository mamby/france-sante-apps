package net.mamby.health.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val request = OneTimeWorkRequestBuilder<ReminderReconciliationWorker>()
            .addTag(WorkManagerReminderScheduler.REMINDER_WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RECONCILIATION_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val RECONCILIATION_WORK_NAME = "health-reminder-reconciliation"
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
