package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup

/**
 * The two rungs, behind the one destination everything else already talks to.
 *
 * There is exactly one place in the app that decides which rung is in force, and this is
 * it: [VaultState.destination] is read on each operation and the call goes to the rung it
 * names. Everything above — the vault and its three triggers, the backup screen, the kept
 * copies screen, the reader behind a copy's sheet — was written against
 * [BackupDestination] and gains the second rung without a line changing, which is what
 * design D3 means by *the second rung is an implementation of the destination, not a
 * rewrite*.
 *
 * **The rung is read per operation and never held.** Somebody points at a folder while the
 * kept-copies screen is open; the next listing has to be of the folder. Resolving once at
 * construction would leave a view model writing into the rung that was in force when it was
 * built, which is the one way a copy can land somewhere nobody is looking.
 *
 * **Both rungs stay reachable at once, and that is deliberate.** Switching folders copies
 * and never moves (design D13), so whatever performs that switch needs to read one rung
 * while writing the other; a router that could only see the rung in force would make it
 * impossible. Tasks 11.8 and 11.10 are built out of the two halves this holds.
 *
 * Nothing is copied when the rung changes — see
 * [com.neoutils.finsight.domain.vault.VaultFolder.pointAt]. The copies on the rung left
 * behind are left exactly where they are: unswept and unlisted, but never removed.
 */
class VaultDestinations(
    private val state: BackupVaultRepository,
    private val appStorage: BackupDestination,
    private val folder: BackupDestination,
) : BackupDestination {

    private val inForce: BackupDestination
        get() = when (state.observe().value.destination) {
            VaultDestination.APP_STORAGE -> appStorage
            VaultDestination.USER_FOLDER -> folder
        }

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = inForce.put(capturedPath, name)

    override suspend fun list(): Either<BackupError, List<StoredBackup>> = inForce.list()

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = inForce.copyOut(backup, destinationPath)

    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> =
        inForce.remove(backup)
}
