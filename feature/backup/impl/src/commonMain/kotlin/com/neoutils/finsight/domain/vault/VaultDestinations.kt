package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import kotlinx.coroutines.flow.StateFlow

/**
 * The two rungs, behind the one destination everything else already talks to.
 *
 * There is exactly one place in the app that decides which rung is in force, and this is
 * it: [VaultRung] is read on each operation and the call goes to the rung it names.
 * Everything above — the vault and its three triggers, the backup screen, the kept copies
 * screen, the reader behind a copy's sheet — was written against [BackupDestination] and
 * gains the second rung without a line changing, which is what design D3 means by *the
 * second rung is an implementation of the destination, not a rewrite*.
 *
 * **The rung is read per operation and never held.** Somebody points at a folder while the
 * kept-copies screen is open; the next listing has to be of the folder. Resolving once at
 * construction would leave a view model writing into the rung that was in force when it was
 * built, which is the one way a copy can land somewhere nobody is looking. The link moves
 * for the same reason and on the same terms: a folder that stopped being reachable between
 * two captures sends the second one inside the app.
 *
 * **A folder that cannot be reached sends the copies inside the app, and says nothing about
 * it here.** That is [VaultRung]'s rule, not a second one — the choice is untouched, the
 * fallback is provisional, and announcing it is the screen's (design D12). What this
 * guarantees is the half the spec states as a prohibition: *o app MUST NOT deixar de
 * capturar enquanto espera uma resposta*. A router that kept sending copies at a folder that
 * is not there would be a vault writing nothing while its switch says it is on.
 *
 * **Both rungs stay reachable at once, and that is deliberate.** Switching destination
 * copies and never moves (design D13), so whatever performs that switch needs to read one
 * rung while writing the other; a router that could only see the rung in force would make it
 * impossible. [rungFor] is that half, and [VaultMigration] is its only caller.
 *
 * Nothing is copied by the rung changing — see [VaultMigration], which is offered and never
 * performed on its own. The copies on the rung left behind are left exactly where they are.
 */
class VaultDestinations(
    private val state: BackupVaultRepository,
    /**
     * What the last reading said about the folder, from the one place it is read
     * ([VaultFolder.link]). It is a flow rather than a value because the reading changes
     * while the app runs, and this must see the change on the very next operation.
     */
    private val link: StateFlow<FolderLink>,
    private val appStorage: BackupDestination,
    private val folder: BackupDestination,
) : BackupDestination {

    private val rung: VaultRung
        get() = VaultRung(state.observe().value.destination, link.value)

    private val inForce: BackupDestination get() = rungFor(rung.inForce)

    /**
     * One named rung, whichever is in force.
     *
     * It exists for the one operation that has to address both at once: carrying copies from
     * where they were to where they now go reads one and writes the other, and neither of
     * the two is the rung in force for the whole of it (design D13).
     */
    internal fun rungFor(destination: VaultDestination): BackupDestination =
        when (destination) {
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
