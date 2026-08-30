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
 * ([com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME]) and is the
 * only file this app ever writes there, so nothing of the user's is within reach of what
 * [clearedCopyPath] removes.
 */
interface MigrationCopyPlace {

    /**
     * The file this app serves its own archive from — what a migration is about to rewrite,
     * and therefore the only thing that can be asked whether one is pending.
     */
    val archivePath: String

    /**
     * Where to write the copy, with whatever the previous migration left under that name
     * already removed — or null when the app's own storage cannot be reached.
     *
     * Clearing is part of answering, and it is the caller's job by the same rule that keeps
     * `:core:database` free of a file API: `VACUUM INTO` refuses a destination that already
     * holds a file, and the module that runs it neither creates nor removes one. It is why
     * the answer is only ever asked for when a migration really is pending — the copy from
     * the last one stands until the next one replaces it (design D10), and clearing it on an
     * opening that migrates nothing would destroy the very thing retention is told to spare.
     *
     * The journal files go with it, because whatever confirmed or restored that copy opened
     * it with Room, and Room opens in write-ahead logging.
     */
    fun clearedCopyPath(): String?
}
