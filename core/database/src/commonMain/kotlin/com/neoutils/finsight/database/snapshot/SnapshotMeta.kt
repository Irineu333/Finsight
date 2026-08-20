package com.neoutils.finsight.database.snapshot

/**
 * The single table a captured file carries about itself, and the only one in it that is
 * not part of the schema.
 *
 * It lives nowhere else: no `@Entity`, no place in `AppDatabase`, no migration. An
 * entity would cost a schema migration in production to store what only means anything
 * while the file is away from the app, and a restore would carry a stranger's stamp back
 * into the live database.
 *
 * Whoever writes it is whoever reads it, and both are here — the name, the columns and
 * the version of the convention in one place, so renaming a field is one change instead
 * of two, with the forgotten half quietly degrading into "unknown origin" and nothing
 * failing.
 */
internal object SnapshotMeta {

    /**
     * Also what a restore leaves out of the copy: the table exists in the file and never
     * travels back.
     */
    const val TABLE = "snapshot_meta"

    /**
     * The version of this convention — not of the schema, which already travels in
     * `user_version`, and not of the app, which has a column of its own. It costs an
     * integer and buys a plain refusal the day the convention changes, instead of an
     * obscure failure.
     */
    const val FORMAT_VERSION = 1L

    const val CREATE = """
        CREATE TABLE `$TABLE` (
            `formatVersion` INTEGER NOT NULL,
            `appVersion` TEXT NOT NULL,
            `platform` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL
        )
    """

    /** Written once, so reading the stamp never has to choose between rows. */
    const val INSERT = "INSERT INTO `$TABLE` " +
        "(`formatVersion`, `appVersion`, `platform`, `createdAt`) VALUES (?1, ?2, ?3, ?4)"
}
