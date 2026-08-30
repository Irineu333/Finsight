package com.neoutils.finsight.feature.backup.api

/**
 * Asked once, when the app is opened, whether the interval has run out — and left to take
 * the copy if it has.
 *
 * **The promise is "the first opening after N days", never "every N days"** (design D5),
 * and this contract is the shape of that promise: one call at one moment, with nothing
 * scheduled, nothing woken and nothing running while the app is not. No supported platform
 * lets an app keep the other sentence — iOS guarantees only that a background task will not
 * start *before* the date asked for, and Android stops running background work for a
 * hibernated app in silence — so an app that offered it would be promising what it cannot
 * do.
 *
 * It follows from that shape that being closed for months costs one copy on reopening and
 * not one per interval that went by: what elapsed is a *condition* asked at an occasion,
 * never a count of occasions owed.
 *
 * Like the archive's own upkeep, this is fired and forgotten. Nothing waits on it, no screen
 * observes it, and it neither returns an outcome nor throws: a copy that could not be taken
 * leaves the instant of the last successful one where it was, which is the line the screen
 * shows and the only way anybody finds out the protection stopped (design D12).
 */
fun interface PeriodicBackup {

    /**
     * Takes the copy this opening owes, if it owes one.
     *
     * Returning promises nothing was written. The vault may be off, this trigger may be
     * switched off on its own, the interval may not have run out, and the copy already
     * kept may still hold everything the archive does (design D8) — in all four there is
     * nothing a new file would add.
     */
    suspend fun captureIfDue()

    companion object {

        /**
         * Does nothing and copies nothing. Not a default — the app binds the vault — but
         * what a test whose subject is elsewhere hands to the thing it is testing.
         */
        val None = PeriodicBackup { }
    }
}
