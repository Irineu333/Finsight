package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFolder
import com.neoutils.finsight.domain.vault.service.FolderLink
import com.neoutils.finsight.domain.vault.service.NEWEST_FIRST
import com.neoutils.finsight.domain.vault.service.NoBackupFolder
import com.neoutils.finsight.domain.vault.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.domain.vault.service.UnreachableDestination
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
 * **A folder change leaves two folders reachable, not one, and [rungFor] is what tells them
 * apart (task 11.10).** The app remembers exactly two tokens for the folder rung — the one
 * currently pointed at, and the one [BackupFolder.point] most recently shifted aside — so a
 * [VaultLocation] naming the folder just left still resolves, through [previousFolder],
 * for exactly as long as an offer built from it could still be answered. This is why the
 * router is keyed by [VaultLocation] and not by [VaultDestination]: the enum has two values
 * and the folder rung can name any number of folders over an install's life, and *whichever
 * one is pointed at now* is not the question a carry offered right after a change is asking.
 *
 * Nothing is copied by the rung changing — see [VaultMigration], which is offered and never
 * performed on its own. The copies on the rung left behind are left exactly where they are.
 *
 * **One copy is read from outside the rung in force, and only one**: the one taken before a
 * migration, which goes into the app's own storage whatever destination is chosen because on
 * the way up a folder may not be reachable. A listing goes and gets it, and a read or a
 * removal of it addresses the app's own storage — see [list] and [holderOf]. Nothing is ever
 * *written* anywhere but the rung in force.
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
    /**
     * The same folder [folder] currently reads and writes, asked here only for its
     * [BackupFolder.identity] — never for [BackupFolder.point] or anything else that could
     * open it. [NoBackupFolder] answers no identity at all, which is what a platform with no
     * folder rung correctly gives this.
     */
    private val folderToken: BackupFolder = NoBackupFolder,
    /**
     * The folder rung the person most recently left, exactly as [folder] but built on the
     * token [BackupFolder.point] shifted aside rather than the one it just wrote (task
     * 11.10). [UnreachableDestination] is correct here on a platform with no folder rung, and
     * on the ordinary run of an install that has changed folders at most once.
     */
    private val previousFolder: BackupDestination = UnreachableDestination,
    /** [folderToken]'s counterpart for [previousFolder]. */
    private val previousFolderToken: BackupFolder = NoBackupFolder,
) : BackupDestination {

    private val rung: VaultRung
        get() = VaultRung(state.observe().value.destination, link.value)

    /**
     * The rung an ordinary read or write actually goes to — [rung]'s own two-way choice,
     * answered without asking which physical folder either name is. Every folder this app
     * has ever pointed at reads [VaultDestination.USER_FOLDER] the same way, so there is
     * nothing here for [VaultLocation] to add: the identity question only exists once there
     * are two folders that could both answer to that one name, which is [rungFor]'s alone.
     */
    private val inForce: BackupDestination
        get() = when (rung.inForce) {
            VaultDestination.APP_STORAGE -> appStorage
            VaultDestination.USER_FOLDER -> folder
        }

    /**
     * The rung a [VaultLocation] actually names — [VaultMigration]'s only caller, since
     * carrying is the one operation that has to address the folder just left and the one now
     * in force at once (design D13; task 11.10).
     *
     * App storage always resolves to itself. A folder resolves by identity: to [folder] when
     * it matches [folderToken] — whatever is pointed at right now — and to [previousFolder]
     * when it matches [previousFolderToken] — the one folder change ago. Anything else names
     * a folder this app no longer has a token for at all, which is [UnreachableDestination]
     * rather than a silent misroute: this app keeps exactly two tokens for the folder rung,
     * current and previous, and never a longer trail.
     */
    internal fun rungFor(location: VaultLocation): BackupDestination = when {
        location.destination == VaultDestination.APP_STORAGE -> appStorage
        location.folder != null && location.folder == folderToken.identity -> folder
        location.folder != null && location.folder == previousFolderToken.identity -> previousFolder
        else -> UnreachableDestination
    }

    /**
     * Always the rung in force. The copy taken before a migration is the one file this
     * router reads from somewhere else, and it is never written from here — it carries a
     * reserved name nothing else asks for, and the place it goes is the migration's own
     * (see [MigrationCopyPlace]).
     */
    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = inForce.put(capturedPath, name)

    /**
     * The rung in force, plus the copy taken before a migration wherever that rung is not
     * the app's own storage.
     *
     * That copy goes into the app's own storage whatever destination is chosen, because on
     * the way up a folder may not be reachable and the capture must not depend on one — and
     * the copy exists for a migration that finished without an error and wrote something
     * wrong, which is found out days later. Listing only the rung in force would leave it
     * written and unreachable for exactly the person who chose a folder: never shown, never
     * restorable, which is the invisible copy design D1 refuses.
     *
     * It joins the listing rather than replacing anything, and joins it in order, so
     * everything reading a destination — the kept-copies screen, the line on the backup
     * screen counting what is there — answers the same. Retention does not count it and
     * never sweeps it; that exclusion is [BackupVault]'s and is by the same reserved name.
     */
    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        inForce.list().map { copies -> copies.withPreMigrationCopy() }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = holderOf(backup).copyOut(backup, destinationPath)

    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> =
        holderOf(backup).remove(backup)

    /**
     * The rung a copy [list] handed out actually sits on: the app's own storage for the one
     * taken before a migration, and the rung in force for everything else.
     *
     * The reserved name is enough to tell them apart because nothing else may carry it —
     * captures are dated, imports are dated and marked, and a carry leaves it behind on the
     * rung it belongs to ([VaultMigration]). So a copy under that name is the migration's,
     * and it is in the one place migrations write.
     */
    /**
     * The rung in force, handed over as itself — so a caller holding this addresses one
     * rung for as long as it holds it, where every call through the router would resolve
     * again.
     *
     * **Reading the rung per operation is right for this class and wrong inside a capture**
     * (see [BackupDestination.resolved]). Somebody points at a folder while the kept-copies
     * screen is open and the next listing has to be of the folder; a capture, on the other
     * hand, writes a file and then sweeps what is past the limit, and those two have to be
     * the same place. Answering the rung rather than `this` is how a caller says which of
     * the two it is.
     *
     * The copy taken before a migration is not carried over with it, and does not need to
     * be: a rung answered here is written to and swept, and that copy is neither — it is
     * read from the app's own storage by [list] and addressed by [holderOf], both of which
     * stay on the router for every caller that goes on using it.
     */
    override fun resolved(): BackupDestination = inForce

    private fun holderOf(backup: StoredBackup): BackupDestination =
        if (backup.name == PRE_MIGRATION_BACKUP_NAME) appStorage else inForce

    private suspend fun List<StoredBackup>.withPreMigrationCopy(): List<StoredBackup> {
        if (rung.inForce == VaultDestination.APP_STORAGE) return this

        val kept = appStorage.list().getOrNull()
            ?.firstOrNull { it.name == PRE_MIGRATION_BACKUP_NAME }
            ?: return this

        return (this + kept).sortedWith(NEWEST_FIRST)
    }
}
