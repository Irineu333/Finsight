package com.neoutils.finsight.domain.vault

/**
 * How far the archive has got: a number that goes up when a row is added and stands still
 * when one is removed.
 *
 * It is the whole of the precondition every trigger shares (design D8). A copy is still
 * good while the archive has not gone past the mark that copy holds, and the reason is not
 * frugality: **a deletion never makes the previous copy insufficient — that copy is
 * precisely the more complete of the two.** Only what was added since it was taken is
 * unprotected, so only that is worth another file. Twenty deletions in a row therefore
 * produce one copy, an invoice that removes its transactions one by one in a loop produces
 * one, and somebody who opens the app every day without entering anything accumulates
 * none.
 *
 * ### The conservative rule this was chosen over
 *
 * Design D8 records a plan B, and it is the safer of the two: **"anything at all was
 * written since the last copy"** — read from a signal that moves on every committed write,
 * such as the database header's change counter or Room's own invalidation. It never leaves
 * a hole, because it cannot tell one kind of write from another and so misses none.
 *
 * It was not chosen because what it costs is exactly what D8 exists to prevent: a run of
 * deletions writes one file per deletion. The preventive trigger runs *before* each
 * destructive action, so by the second deletion the first has already committed, and a
 * counter that merely says "something was written" says yes every single time. That is
 * twenty near-identical files for twenty deletions, and the one thing they have in common
 * is that the first of them already held everything the other nineteen do.
 *
 * The price of the rule that was chosen is that a write which adds nothing new to count —
 * a facade renamed in place, a row entered in one of the two tables whose key is not
 * generated — leaves the mark where it was, and the copy taken before it stays in force
 * until something is added. Money never falls in that gap: an edited transaction has its
 * entries rewritten rather than updated (`LedgerEntryWriter.rewriteEntries` deletes them
 * and writes them again), so an edit moves the mark exactly as an entry would.
 *
 * ### What a mark is not
 *
 * It is not a count of rows, and it must not become one. A count falls with a deletion, so
 * "delete three, add one" would leave it below where it was and report an archive that has
 * gone backwards while holding a row no copy has. What is counted here only ever grows.
 */
fun interface ArchiveMark {

    /**
     * The archive's mark right now.
     *
     * Comparable only with another value from the same archive, and meaningful only in one
     * direction: greater means rows exist that the older mark did not cover. The number
     * itself says nothing anybody should render.
     */
    suspend fun current(): Long
}
