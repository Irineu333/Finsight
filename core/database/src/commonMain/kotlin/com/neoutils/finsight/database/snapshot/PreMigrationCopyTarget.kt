package com.neoutils.finsight.database.snapshot

/**
 * Asked, while the database is being assembled, where the copy taken before a migration
 * should go — and answering *nowhere* is a whole answer.
 *
 * It is the port that lets something outside decide, without this module learning what it
 * decides *from*. `getDatabaseBuilder` already states the rule as a parameter — a path, and
 * it captures; none, and it does not (design D11) — and this is only how that path arrives
 * at the one place that calls it: whoever assembles the database asks, and passes on what
 * it is told. Nothing here reads a preference, and there is nothing in this contract that
 * could carry one.
 *
 * It is the same shape as the ledger's ports, for the same reason: one implementer, claimed
 * in its own Koin module, and unclaimed is a valid graph — nobody registering one means no
 * copy is ever taken, which is exactly what a build with nothing to decide from should do.
 *
 * The file at the path is the caller's from beginning to end. Making a path free to write
 * to is part of answering — `VACUUM INTO` refuses a destination that already holds a file —
 * and this module neither creates nor removes one, here as everywhere else.
 *
 * **It is two calls and not one, because a capture that does not happen must cost nothing.**
 * The `VACUUM` behind [path] is refused by a full disk, which is the very condition that
 * makes somebody want the copy they already have, and it is swallowed where it happens; the
 * process can also be killed between the two. So [path] answers somewhere a failure is
 * harmless and [settle] is what puts a copy in force, once the attempt has been made and
 * whatever it produced can be looked at.
 */
interface PreMigrationCopyTarget {

    /**
     * Where to write the copy, or null for nowhere.
     *
     * Asked once, as the builder is put together, and therefore before anything opens the
     * database. An implementation that touches the file system does so on the thread that
     * first asks for the database.
     *
     * It is never the copy already in force: answering is not the moment to destroy
     * anything, because nothing yet guarantees a replacement.
     */
    fun path(): String?

    /**
     * Says that the attempt at [path] is over, whatever came of it — so that what it wrote
     * may be put in force, or thrown away.
     *
     * Called once, immediately after the builder is assembled and therefore after the
     * capture has been tried, on every opening: an opening that was answered *nowhere* has
     * nothing to settle and this does nothing.
     */
    fun settle()
}
