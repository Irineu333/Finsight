package com.neoutils.finsight.domain.vault.service

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.BackupError

/**
 * The folder rung on a platform that has not built it yet, and — since task 11.10 — a
 * [com.neoutils.finsight.domain.vault.VaultLocation] naming a folder that is neither the one
 * currently pointed at nor the one just left: every operation refuses, and none of them lies.
 *
 * It is bound beside [NoBackupFolder] and is unreachable for the same reason — nothing can
 * move the vault onto a folder rung nothing can address — so what this is for is the one
 * thing worse than refusing: a folder destination that silently wrote into the app's own
 * storage would tell somebody their copies were outside the app while they were not.
 *
 * **[list] refuses rather than answering nothing**, which is the whole of design D9: zero
 * copies means *could not read*, never *there is nothing here*, and a destination that has
 * never existed has certainly not been read.
 *
 * **This is [com.neoutils.finsight.domain.vault.VaultDestinations.rungFor]'s answer to a
 * third folder.** This app remembers exactly two tokens for the folder rung — current and
 * previous — and never a longer trail, so a carry offered from a folder two changes back is
 * a carry from a place nothing here can still address, and refusing is the honest answer
 * rather than reading it as empty.
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
