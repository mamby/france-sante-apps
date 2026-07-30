package net.mamby.health.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = when (val result = backupRepository.backupNow()) {
        is BackupOperationResult.Success -> Result.success()
        is BackupOperationResult.Failure -> if (result.retryable) Result.retry() else Result.failure()
        BackupOperationResult.NotConfigured -> Result.success()
        is BackupOperationResult.InvalidPassphrase -> Result.failure()
    }
}
