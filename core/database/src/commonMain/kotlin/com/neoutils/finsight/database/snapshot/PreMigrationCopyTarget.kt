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
 * The file at the path is the caller's from beginning to end. Removing whatever the
 * previous migration left there is part of answering — `VACUUM INTO` refuses a destination
 * that already holds a file — and this module neither creates nor removes one, here as
 * everywhere else.
 */
fun interface PreMigrationCopyTarget {

    /**
     * Where to write the copy, or null for nowhere.
     *
     * Asked once, as the builder is put together, and therefore before anything opens the
     * database. An implementation that touches the file system does so on the thread that
     * first asks for the database.
     */
    fun path(): String?
}
