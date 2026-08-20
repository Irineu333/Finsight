package com.neoutils.finsight.database.snapshot

import androidx.room.PooledConnection
import androidx.room.Transactor
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseRestoreError
import com.neoutils.finsight.database.exception.DatabaseRestoreException
import com.neoutils.finsight.database.extension.SQLITE_FULL
import com.neoutils.finsight.database.extension.resultCode

/**
 * Makes this database hold what the file at [candidatePath] holds, in one transaction and
 * without closing anything.
 *
 * The file is attached and every table is emptied and refilled from it, so the archive
 * either becomes the file's in full or stays exactly as it was. [candidatePath] is meant to
 * be a file [CandidateVerifier] has approved: nothing here reads what it holds as evidence,
 * and the refusals left are the machine's.
 *
 * Reopening the database is not an alternative, and that is what shapes the procedure. The
 * connection used is Room's own writer — the one carrying the temporary triggers that make
 * invalidation happen — so the flows already collecting re-emit on their own once the block
 * returns, and every instance handed out by injection goes on working. Closing and
 * rebuilding would strand both.
 *
 * `ATTACH` runs before the transaction and `DETACH` after it, because SQLite allows the
 * first inside a transaction and refuses the second. The `DETACH` is in a `finally`: a
 * transaction that rolls back still leaves the file attached, and the name would be taken
 * the next time anyone tried.
 *
 * @throws DatabaseRestoreException when the write order cannot be derived, or SQLite
 * refuses a statement of the replacement.
 */
suspend fun AppDatabase.replaceContentFrom(candidatePath: String) {
    try {
        useWriterConnection { connection ->
            connection.requireForeignKeysEnforced()
            val order = connection.copyOrder()
            connection.usePrepared(ATTACH) { statement ->
                statement.bindText(1, candidatePath)
                statement.step()
            }
            try {
                connection.rewriteTables(order)
            } finally {
                connection.execSQL(DETACH)
            }
        }
    } catch (cause: SQLiteException) {
        throw DatabaseRestoreException(cause.toRestoreError(), cause)
    }
}

/**
 * Empties every table and refills it from the attached file, in a single transaction:
 * children before their parents on the way out, parents before their children on the way
 * in.
 *
 * The two phases fail differently, and only one of them depends on the order. Emptying does
 * not: every table ends empty whichever way round the loop runs, because `ON DELETE CASCADE`
 * and `SET NULL` only reach rows a later statement would have removed anyway, and they
 * succeed without saying so. Refilling does: foreign keys are enforced at the end of every
 * statement, so a child written before its parent is refused there and the whole transaction
 * rolls back. Between the two, the archive becomes the file's in full or stays as it was, and
 * never something the file does not describe — which is why nothing reads it back afterwards.
 *
 * Turning enforcement off would sidestep the order entirely, and is refused: the pragma is
 * per connection and persistent, so an exception between switching it off and back on would
 * leave the app's only writer without foreign keys until the process dies. Deferring the
 * check to the commit does not work at all — it does not hold for `INSERT … SELECT` reading
 * from an attached database, where the commit fails even though the final state is
 * consistent.
 */
private suspend fun Transactor.rewriteTables(order: List<String>) {
    immediateTransaction {
        order.asReversed().forEach { table -> execSQL("DELETE FROM `main`.`$table`") }
        order.forEach { table ->
            execSQL("INSERT INTO `main`.`$table` SELECT * FROM `$CANDIDATE`.`$table`")
        }
    }
}

/**
 * Refuses to start unless foreign keys are enforced on this connection.
 *
 * Everything the replacement relies on rests here. With enforcement on, a child written
 * before its parent is refused and the transaction rolls back; with it off, the same order
 * commits an archive whose rows point at nothing, and no later reading of the result would
 * find it — a check over the finished database reports what the file itself already
 * satisfied. So the premise is what gets checked, in one row read before anything is
 * attached, rather than the consequence, in a sweep of the whole database afterwards.
 */
private suspend fun PooledConnection.requireForeignKeysEnforced() {
    val enforced = usePrepared(FOREIGN_KEYS) { statement ->
        statement.step()
        statement.getLong(0)
    }
    if (enforced != 1L) {
        throw DatabaseRestoreException(DatabaseRestoreError.FOREIGN_KEYS_DISABLED)
    }
}

/**
 * The tables to copy, parents before children.
 *
 * Both the tables and their keys are read from the database itself, at the moment of the
 * operation, and from `main` rather than from the attached file: `main` is the schema being
 * written, and the file carries a table of its own that has no place in it. A list kept by
 * hand would have to be remembered at every schema change, and forgetting it would surface
 * as a failed restore long after the change that caused it.
 */
private suspend fun PooledConnection.copyOrder(): List<String> {
    val tables = usePrepared(TABLES) { statement ->
        buildList { while (statement.step()) add(statement.getText(0)) }
    }.toSet()
    return orderedByDependency(
        tables.associateWith { table -> parentsOf(table).intersect(tables) - table }
    )
}

/** The tables [table] points at through its foreign keys. */
private suspend fun PooledConnection.parentsOf(table: String): Set<String> =
    usePrepared(PARENTS) { statement ->
        statement.bindText(1, table)
        buildSet { while (statement.step()) add(statement.getText(0)) }
    }

/**
 * A topological order over [parents]: a table comes after every table it points at.
 *
 * Alphabetical among tables that do not depend on one another, so the same schema always
 * produces the same order and a failure is reproducible.
 *
 * Foreign keys that form a cycle admit no such order, and saying so costs one comparison
 * where the alternative is a loop that never ends. Nothing in the schema forms one today,
 * which is precisely why the condition has to be named rather than trusted.
 */
private fun orderedByDependency(parents: Map<String, Set<String>>): List<String> {
    val pending = parents.keys.toMutableSet()
    val ordered = mutableListOf<String>()
    while (pending.isNotEmpty()) {
        val ready = pending.filter { table -> parents.getValue(table).none { it in pending } }
        if (ready.isEmpty()) throw DatabaseRestoreException(DatabaseRestoreError.CYCLIC_FOREIGN_KEYS)
        ordered += ready
        pending -= ready
    }
    return ordered
}

/**
 * A full disk is 13, and it is the one condition the caller answers differently. Everything
 * else is [DatabaseRestoreError.UNKNOWN], with the result code and the wording kept in the
 * cause: past an approved file, a refusal here is a fault of this build's own SQL rather
 * than something the person holding the file can act on.
 */
private fun SQLiteException.toRestoreError(): DatabaseRestoreError = when (resultCode()) {
    SQLITE_FULL -> DatabaseRestoreError.NO_SPACE
    else -> DatabaseRestoreError.UNKNOWN
}

/**
 * The name the candidate file is attached under, which has to be free of the schema names
 * already in use — `main` and `temp`.
 */
private const val CANDIDATE = "candidate"

private const val ATTACH = "ATTACH DATABASE ?1 AS `$CANDIDATE`"

private const val DETACH = "DETACH DATABASE `$CANDIDATE`"

/** Written by the Android framework's own SQLite, and never by this app. */
private const val ANDROID_METADATA = "android_metadata"

/**
 * What the copy is about: the user's own rows.
 *
 * Left out is everything a database keeps about itself — the sequence counters and every
 * other `sqlite_` table, the locale row a database opened by the Android framework carries,
 * and the schema identity, which belongs to the database in use and not to a file it reads.
 * The stamp a captured file carries is left out by the same rule and named by the constant
 * that creates it, so it stays out of the copy however the tables are arrived at. Views and
 * indexes are not tables and hold nothing of their own.
 */
private const val TABLES = """
    SELECT `name` FROM `main`.`sqlite_master`
    WHERE `type` = 'table'
      AND `name` NOT LIKE 'sqlite_%'
      AND `name` NOT IN ('$ROOM_MASTER_TABLE', '$ANDROID_METADATA', '${SnapshotMeta.TABLE}')
    ORDER BY `name`
"""

private const val PARENTS = "SELECT `table` FROM pragma_foreign_key_list(?1)"

private const val FOREIGN_KEYS = "PRAGMA foreign_keys"
