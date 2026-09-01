package com.neoutils.finsight.database.repository

import androidx.room.Transactor
import androidx.room.useReaderConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.domain.vault.ArchiveMark

/**
 * The archive's mark, read as the highest row id SQLite has ever handed out in it.
 *
 * Every table of this schema but two gives its rows a generated key, and SQLite keeps the
 * highest one it has issued per table in `sqlite_sequence`, where it is raised by an insert
 * and — this being the property the whole precondition rests on — **never lowered by a
 * delete**. Their sum is therefore a number that grows with what is added and does not
 * move for what is taken away, which is exactly what design D8 asks a copy to be measured
 * against.
 *
 * It comes from the archive itself rather than from a counter this app keeps, so nothing
 * that happens to this app's process — a restart, a reinstall reading the same file — can
 * put it out of step with what the archive holds.
 *
 * What it cannot report is a restore. Replacing the archive with a file's content issues no
 * id of its own, so the mark says nothing happened while the archive became a different one
 * altogether; a copy stops covering it for a reason that is not about its size and that no
 * reading of it expresses. Coverage is ended there by whoever replaced the archive — see
 * [com.neoutils.finsight.domain.vault.BackupVault.archiveReplaced] — and never deduced here.
 *
 * `sqlite_sequence` is created by SQLite the first time a generated key is issued, so on an
 * archive nobody has entered anything into it does not exist yet. Its absence is answered
 * as zero, which is what it means; a failure to read is not caught here, because a
 * precondition that cannot be evaluated must not quietly become a *no*.
 */
class RoomArchiveMark(private val database: AppDatabase) : ArchiveMark {

    override suspend fun current(): Long = database.useReaderConnection { connection ->
        if (connection.scalarLong(SEQUENCE_TABLE_EXISTS) == 0L) 0L
        else connection.scalarLong(HIGHEST_ISSUED)
    }
}

private suspend fun Transactor.scalarLong(sql: String): Long = usePrepared(sql) { statement ->
    if (statement.step()) statement.getLong(0) else 0L
}

private const val SEQUENCE_TABLE_EXISTS =
    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'"

private const val HIGHEST_ISSUED = "SELECT COALESCE(SUM(seq), 0) FROM sqlite_sequence"
