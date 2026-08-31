package com.neoutils.finsight.domain.vault

/**
 * The two files the copy taken before a migration is about, spelled out for the one moment
 * they have to be paths: the archive that is about to be rewritten, and where its copy goes.
 *
 * **This is not a [com.neoutils.finsight.ui.screen.backup.service.BackupDestination] and it
 * does not weaken design D2.** A destination hands nobody a path because on two platforms it
 * cannot — a folder the user pointed at is a security-scoped `NSURL` or a tree `Uri`, and
 * turning either into text destroys it. This is the app's own storage on all three
 * platforms, which is a real path everywhere and always reachable, and the spec puts the
 * copy there for exactly that reason: the app is coming up, and a folder outside it may not
 * be reachable at all. What forces paths here is the mechanism — `VACUUM INTO` writes to a
 * path, and it runs before anything of this feature is on its feet, in a plain function that
 * cannot suspend and so cannot ask a destination for anything.
 *
 * The copy is written under one reserved name
 * ([com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME]), reached
 * through one staging name beside it, and those two are the only files this app ever writes
 * there — so nothing of the user's is within reach of anything below.
 */
interface MigrationCopyPlace {

    /**
     * The file this app serves its own archive from — what a migration is about to rewrite,
     * and therefore the only thing that can be asked whether one is pending.
     */
    val archivePath: String

    /**
     * Where a new copy is written, with whatever an earlier attempt left there already
     * removed — or null when the app's own storage cannot be reached.
     *
     * **It is never the copy in force.** The file lands under a name nothing lists, nothing
     * counts and nothing sweeps
     * ([com.neoutils.finsight.ui.screen.backup.service.STAGED_PRE_MIGRATION_NAME]), so a
     * `VACUUM` refused by a full disk, a process killed halfway, or the well-formed empty
     * database a full volume leaves behind all cost nothing at all: the copy from the last
     * migration is still there, still listed, still the one a restore reaches.
     *
     * Clearing what an earlier attempt left is part of answering, by the same rule that
     * keeps `:core:database` free of a file API: `VACUUM INTO` refuses a destination that
     * already holds a file, and the module that runs it neither creates nor removes one.
     *
     * The journal files go with it, because whatever confirmed or restored a copy opened it
     * with Room, and Room opens in write-ahead logging.
     */
    fun stagedCopyPath(): String?

    /**
     * Puts what [stagedCopyPath] answered in force when [keep], and removes the staged file
     * either way.
     *
     * Whether it is worth keeping is not decided here — it is
     * [VaultPreMigrationCopy]'s, which asks the same question of the staged file that it
     * asked of the archive. This is only the file work: replace the copy under the reserved
     * name, and take the journal files of the copy that was replaced with it.
     *
     * Nothing is removed when [keep] is false. A copy in force is only ever replaced by one
     * that has been read as a database, which is the whole point of writing the new one
     * somewhere else first.
     */
    fun settleStagedCopy(keep: Boolean)
}
