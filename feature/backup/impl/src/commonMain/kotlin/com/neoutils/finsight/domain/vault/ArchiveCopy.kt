@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Which of the kept copies the archive was last identical to, because it was taken from the
 * archive or because the archive was restored from it.
 *
 * **Last identical to, and not still identical to.** It is written at those two moments and
 * at no other, so an entry made a second later leaves it exactly where it was — and it has
 * to, because nothing here could detect that: there is no reading of the live archive that
 * says which file it came from, which is the same reason it is recorded rather than derived
 * (below). Whoever renders it therefore may say *this was the last copy the two agreed on*
 * and may not say *the app's data is this copy's*, which stops being true at the first
 * transaction somebody enters and would go on being displayed.
 *
 * ### Why it is recorded and not derived
 *
 * There is no reading of the live archive that answers this. The stamp a captured file
 * carries is deliberately left out of a restore — `DatabaseRestore` excludes
 * `SnapshotMeta.TABLE` from the tables it copies, and the table is in no migration and no
 * entity — so the archive in use carries no provenance of its own, before or after a
 * restore. Deriving it instead by comparing the archive against each copy would mean
 * reading every file the moment the list opens, which on a pathless destination is copying
 * every copy out to a temporary. So it is written down at the two moments it becomes true,
 * and at no other.
 *
 * ### Why it is not coverage
 *
 * [VaultState.markAtLastCapture] answers a different question — *does a copy still hold
 * everything the archive holds?* — and the two part company exactly where this feature is
 * needed. A completed restore gives coverage up on purpose
 * ([BackupVault.archiveReplaced]), so that the next trigger takes a copy of the archive it
 * has just become; asking coverage which copy the person is standing on right after a
 * restore answers *none*, which is correct for deciding whether to capture and useless for
 * saying where they are. Two facts, two fields, and the coverage rule is untouched.
 *
 * ### Why it cannot become a second history
 *
 * It names a copy; it never asserts that one exists (design D9). Every use of it goes
 * through [describes] against the rows the destination has just answered with, so a copy
 * removed by retention, by another install or from a file manager simply stops matching
 * and nothing is marked. The folder remains the only thing that says what is there.
 *
 * It lives with the rest of the vault's state, in this install's settings rather than in
 * the archive, for the reason the history is not a table either: a value inside the file
 * would travel in every copy and come back in time with a restore, describing a folder it
 * had stopped being about.
 *
 * @param savedAt the copy's own instant as the destination reports it, which is what makes
 * the pointer self-invalidating. A file rewritten under a name already recorded — the one
 * reserved name a pre-migration copy takes is the case that exists — no longer matches,
 * and an unmarked list is the honest answer where a stale mark would be a wrong one.
 */
data class ArchiveCopy(
    val name: String,
    val savedAt: Instant,
) {

    /** Whether [copy] is the one this names, among the copies a destination just listed. */
    fun describes(copy: StoredBackup): Boolean =
        copy.name == name && copy.savedAt == savedAt
}

/** How a copy that landed, or one just restored from, states itself as [ArchiveCopy]. */
fun StoredBackup.asArchiveCopy() = ArchiveCopy(name = name, savedAt = savedAt)
