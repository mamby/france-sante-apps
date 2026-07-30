package net.mamby.health.backup

import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import net.mamby.health.settings.BackupStatus

interface BackupRepository {
    val status: Flow<BackupStatus>

    suspend fun configure(
        destination: Uri,
        passphrase: CharArray,
        scheduled: Boolean = true,
    ): BackupOperationResult

    suspend fun backupNow(): BackupOperationResult

    suspend fun scheduleAfterVaultChange()

    suspend fun prepareRestore(
        source: Uri,
        passphrase: CharArray,
    ): RestorePreparationResult

    suspend fun commitRestore(
        token: UUID,
        crossFlavorConfirmed: Boolean,
    ): RestoreCommitResult

    suspend fun discardRestore(token: UUID)

    suspend fun clearConfiguration()
}
