package com.neoutils.finsight.domain.model

/**
 * Told once a restore has replaced the archive wholesale, so a device preference that
 * indexes the old archive's rows by id can be told it no longer describes anything real.
 *
 * **Why this exists at all.** A restore does not touch this install's own settings — they
 * are facts about the install, and most of them stay true across one (`local-backup`
 * spec). Some do not: a preference that names a facade row by its database id — an account
 * excluded from a total, a card excluded from a pager — is a fact about *this archive's*
 * rows, and a restore can swap the whole archive under it while the preference sits
 * untouched on file. The rows those ids named are gone; the rows they now name, if any,
 * belong to whichever archive just landed and are very unlikely to be the same ones.
 *
 * **Why "the id no longer resolves" is not the check to make.** A fresh archive assigns
 * its own ids from one, independently of any other install's, so a stale id is exactly as
 * likely to land on an unrelated row as on none at all. A preference that filtered itself
 * down to ids that still resolve would leave the collision case — the far more silent one —
 * untouched: a real account, excluded from a total for no reason anybody chose, with
 * nothing to explain it. Forgetting the whole preference is the only answer that is
 * right in both cases; resolving it "safely" is not a thing an id with no other
 * identity to check itself against can be made to do.
 *
 * **The one call and no answer.** [onArchiveReplaced] must not throw: a restore has
 * already landed by the time it is told, so a listener's own failure is a preference left
 * stale, never a reason to report the restore itself as having failed. `ArchiveRestore`
 * calls it best-effort and never lets it surface.
 *
 * A restore issues no ids of its own — it writes back rows a file already carried — so
 * this is never told anything about *which* ids changed, only that they may have. Whoever
 * implements it reads its own preferences and decides what "no longer resolves to
 * anything this install can vouch for" means for them.
 */
fun interface ArchiveReplacedHook {

    /** The archive is a different archive now; forget whatever named its old rows by id. */
    suspend fun onArchiveReplaced()

    companion object {

        /**
         * No preference in the app indexes an archive row by id. Not a default — a restore
         * that silently dropped a real listener would leave a wrong exclusion standing with
         * nothing to explain it, so the caller resolves this explicitly instead of falling
         * back to it by accident. This is for tests whose subject is elsewhere.
         */
        val None = ArchiveReplacedHook {}
    }
}
