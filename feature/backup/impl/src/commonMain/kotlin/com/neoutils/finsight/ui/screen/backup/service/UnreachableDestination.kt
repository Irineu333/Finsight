package com.neoutils.finsight.ui.screen.backup.service

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.BackupError

/**
 * The folder rung on a platform that has not built it yet: every operation refuses, and
 * none of them lies.
 *
 * It is bound beside [NoBackupFolder] and is unreachable for the same reason — nothing can
 * move the vault onto the folder rung where no folder can be pointed at — so what this is
 * for is the one thing worse than refusing: a folder destination that silently wrote into
 * the app's own storage would tell somebody their copies were outside the app while they
 * were not.
 *
 * **[list] refuses rather than answering nothing**, which is the whole of design D9: zero
 * copies means *could not read*, never *there is nothing here*, and a destination that has
 * never existed has certainly not been read.
 *
 * Android replaces it in tasks 11.1–11.3 and iOS in 11.4–11.5.
 */
object UnreachableDestination : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = BackupError.EXPORT_FAILED.left()

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        BackupError.EXPORT_FAILED.left()

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = BackupError.EXPORT_FAILED.left()

    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> =
        BackupError.EXPORT_FAILED.left()
}
