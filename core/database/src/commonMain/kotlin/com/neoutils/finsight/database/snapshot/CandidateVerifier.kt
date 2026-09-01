package com.neoutils.finsight.database.snapshot

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.AppSchema
import com.neoutils.finsight.database.exception.DatabaseVerificationError
import com.neoutils.finsight.database.exception.DatabaseVerificationException
import com.neoutils.finsight.database.exception.MigrationAbortedException
import com.neoutils.finsight.database.exception.UnbalancedLedgerException
import com.neoutils.finsight.database.extension.SQLITE_CORRUPT
import com.neoutils.finsight.database.extension.SQLITE_FULL
import com.neoutils.finsight.database.extension.SQLITE_IOERR
import com.neoutils.finsight.database.extension.declaredSchemaVersion
import com.neoutils.finsight.database.extension.onDatabaseFile
import com.neoutils.finsight.database.extension.resultCode
import com.neoutils.finsight.database.extension.scalarLong
import com.neoutils.finsight.database.extension.verifyDimensionLanding
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The gate a file has to pass before it is allowed to replace the archive in use.
 *
 * Five layers, each refusing something the one before it cannot see: the file is a
 * database, it is one this app wrote, it declares a schema version this build can open,
 * it survives the migration chain and the schema identity check, and what it holds
 * satisfies the invariants of the ledger. Nothing here is precaution — every layer
 * refuses a file that would otherwise be accepted, and being accepted means the user's
 * archive is overwritten with no way back.
 *
 * A refusal and a failure are not the same answer and do not travel together. Every layer
 * states its refusals as a [CandidateRejection], which is a finding about the file; a disk
 * that fills or a device that will not read is the check itself not happening, and leaves
 * as a [DatabaseVerificationException]. Reporting the second as the first would tell
 * someone to pick a different file for a problem no file fixes.
 *
 * None of it touches the database the app is serving. A corrupted file attached to a
 * live connection is reported by SQLite as corruption *of the connection*, which would
 * fire the corruption handling against production; the candidate is only ever opened on
 * connections of its own.
 *
 * The verification *writes* to [verify]'s candidate — migrating it is how it is verified
 * — so the path handed in must be a copy the caller is willing to lose, never the file
 * the user picked.
 *
 * @param openCandidate builds an [AppDatabase] over a given path. Room is what runs the
 * migration chain and checks the schema identity, and assembling it needs a currency
 * seeding and a base currency this module may not name.
 */
class CandidateVerifier(
    private val openCandidate: (String) -> AppDatabase,
) {

    /** @throws DatabaseVerificationException when the check could not be carried out. */
    suspend fun verify(candidatePath: String): CandidateVerification =
        withContext(Dispatchers.Default) {
            readFile(candidatePath)?.let { return@withContext CandidateVerification.Rejected(it) }
            migrate(candidatePath)?.let { return@withContext CandidateVerification.Rejected(it) }
            audit(candidatePath)
        }

    /**
     * Layers 1 to 3, over the throwaway connection [onDatabaseFile] opens — read-only,
     * which adds to what that helper already refuses to do: the flag makes it impossible,
     * rather than merely unintended, for these layers to alter the file the user picked.
     *
     * The machine is separated from the file here too, and it has to be: this is the layer
     * that gets to say "there is nothing here to read as a database", and a device that
     * refused the read says nothing of the kind. Every refusal — a refused open included —
     * asks [raiseIfMachineFailure] first, so only codes about the file reach [toRejection].
     */
    private fun readFile(candidatePath: String): CandidateRejection? = try {
        onDatabaseFile(candidatePath, SQLITE_OPEN_READONLY) { connection ->
            connection.integrityRejection()
                ?: connection.provenanceRejection()
                ?: connection.schemaVersionRejection()
        }
    } catch (cause: SQLiteException) {
        cause.raiseIfMachineFailure()
        cause.toRejection()
    }

    /**
     * Layer 4. Room's `build()` opens nothing, so a connection is acquired to make the
     * migration chain and the schema identity check actually run — the writer, because
     * that is the connection a migration writes on.
     *
     * Three findings are told apart here, because they are three different things about
     * the file. A guard that finds the ledger unbalanced, and a guard that aborts over
     * anything else it was handed, are both speaking about the content, and each says so
     * through the exception it raises; every other refusal is about the schema — an
     * identity hash that is not this one's, a migration this build does not carry, a
     * downgrade — and Room states all of them as an [IllegalStateException].
     *
     * A failure of the machine is none of the three and is not turned into one: the disk
     * filling under a migration is [DatabaseVerificationException], as is any exception
     * this build has no reading of. The [Exception] arm is deliberately not a refusal —
     * an unrecognised failure is exactly the case the wide catch used to mislabel.
     */
    private suspend fun migrate(candidatePath: String): CandidateRejection? {
        val candidate = openCandidate(candidatePath)
        return try {
            candidate.useWriterConnection { }
            null
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: UnbalancedLedgerException) {
            CandidateRejection.UNBALANCED_LEDGER
        } catch (cause: MigrationAbortedException) {
            CandidateRejection.MIGRATION_ABORTED
        } catch (cause: SQLiteException) {
            cause.raiseIfMachineFailure()
            cause.toProvenFileRejection()
        } catch (cause: IllegalStateException) {
            CandidateRejection.SCHEMA_MISMATCH
        } catch (cause: Exception) {
            throw DatabaseVerificationException(DatabaseVerificationError.UNKNOWN, cause)
        } finally {
            candidate.close()
        }
    }

    /**
     * Layer 5 and the reading, over a second throwaway connection on the candidate Room
     * has just migrated and let go of.
     *
     * Room leaves the file in write-ahead logging, so this one is opened for writing: a
     * read-only connection cannot create the shared-memory file a journal left behind
     * would need. Creating what is not there is not a risk any more either — by now the
     * file has been proven to be a database of this app's own schema.
     *
     * "Proven" is Room's word and it is narrower than it sounds: when the file already
     * declares this build's schema version, Room compares the identity hash and validates
     * nothing else. So every statement below runs against tables a hash claimed and nobody
     * read, and a file carrying the hash without the tables raises here rather than at
     * layer 4. That is a refusal — the file said what it was and is not it — and
     * [toProvenFileRejection] is where it is named.
     */
    private fun audit(candidatePath: String): CandidateVerification {
        val connection = try {
            BundledSQLiteDriver().open(candidatePath)
        } catch (cause: SQLiteException) {
            throw DatabaseVerificationException(cause.toVerificationError(), cause)
        }
        try {
            connection.ledgerRejection()?.let { return CandidateVerification.Rejected(it) }
            return CandidateVerification.Accepted(
                origin = connection.readOrigin(),
                counts = connection.readCounts(),
            )
        } catch (cause: SQLiteException) {
            cause.raiseIfMachineFailure()
            return CandidateVerification.Rejected(cause.toProvenFileRejection())
        } finally {
            connection.close()
        }
    }
}

/**
 * Layer 1. `PRAGMA integrity_check` has two ways of saying no and both are answered
 * here: it raises when the bytes are not a database or the pages no longer add up, and
 * it returns a line other than `ok` when it can read the file well enough to describe
 * what is wrong with it.
 *
 * It is not the expensive check a cheaper one would be worth avoiding: a file that is not
 * a database is refused from its first hundred bytes, and a real database of tens of
 * megabytes completes in tens of milliseconds.
 */
private fun SQLiteConnection.integrityRejection(): CandidateRejection? {
    val statement = prepare("PRAGMA integrity_check")
    try {
        val report = if (statement.step()) statement.getText(0) else null
        return if (report == INTEGRITY_OK) null else CandidateRejection.CORRUPTED
    } finally {
        statement.close()
    }
}

/**
 * Layer 2. Room keeps its schema identity in a table of its own, and every database this
 * app has ever written carries it.
 *
 * This is the only layer that separates four files that are perfectly healthy SQLite and
 * simply are not ours: an empty file, a database with no tables, another application's
 * database, and the main file of a database whose schema never left the write-ahead log.
 * All four report `ok` and declare no schema version, and Room would read that as a
 * database yet to be created — creating the schema, seeding it, and handing back
 * something indistinguishable from a backup of an empty archive.
 */
private fun SQLiteConnection.provenanceRejection(): CandidateRejection? =
    if (holdsTable(ROOM_MASTER_TABLE)) null else CandidateRejection.NOT_FROM_THIS_APP

/**
 * Layer 3. The floor of 1 is what keeps Room from creating what it was asked to check:
 * a database declaring version 0 does not exist yet as far as Room is concerned.
 *
 * The ceiling is the file written by a newer build than this one. The file is portable
 * between platforms and the desktop has no automatic update, so a backup taken on a
 * current phone can reach an install that is behind. Room would refuse the downgrade on
 * its own; refusing it here is what lets the screen say why.
 */
private fun SQLiteConnection.schemaVersionRejection(): CandidateRejection? {
    val declared = declaredSchemaVersion()
    return when {
        declared < 1L -> CandidateRejection.NOT_FROM_THIS_APP
        declared > AppSchema.VERSION -> CandidateRejection.SCHEMA_TOO_NEW
        else -> null
    }
}

/**
 * Layer 5. The invariants of the ledger, over the candidate — there is no second
 * implementation of any of them, and these are the ones.
 *
 * The first three are the guards every migration closes with, run in the migrations'
 * order rather than a new one, because the order decides what a file breaking more than
 * one rule is refused for: an entry pointing at a dimension that is not there is also a
 * foreign key violation, and naming the dimension says more.
 *
 * The landing comes last and only here. It needs the dimension to exist and the keys to
 * hold before its answer means anything, and a migration is the wrong place for it: a
 * migration refusing pre-existing data leaves the user unable to open the app at all,
 * which is worse than what it would be refusing. A candidate has somewhere to go back
 * to — the archive it did not replace.
 *
 * They are called one at a time because the last three raise the very same exception,
 * and position is the only thing that tells them apart.
 */
private fun SQLiteConnection.ledgerRejection(): CandidateRejection? {
    try {
        verifyLedgerBalanced(STAGE)
    } catch (cause: UnbalancedLedgerException) {
        return CandidateRejection.UNBALANCED_LEDGER
    }
    try {
        verifyNoOrphanDimensions(STAGE)
    } catch (cause: MigrationAbortedException) {
        return CandidateRejection.ORPHAN_DIMENSION
    }
    try {
        verifyForeignKeys(STAGE)
    } catch (cause: MigrationAbortedException) {
        return CandidateRejection.FOREIGN_KEY_VIOLATION
    }
    try {
        verifyDimensionLanding(STAGE)
    } catch (cause: MigrationAbortedException) {
        return CandidateRejection.MISPLACED_DIMENSION
    }
    return null
}

/** The stamp, or `null` when the file predates it and carries none. */
private fun SQLiteConnection.readOrigin(): SnapshotOrigin? {
    if (!holdsTable(SnapshotMeta.TABLE)) return null
    val statement = prepare(SnapshotMeta.SELECT)
    try {
        if (!statement.step()) return null
        return SnapshotOrigin(
            formatVersion = statement.getLong(0),
            appVersion = statement.getText(1),
            platform = statement.getText(2),
            createdAt = statement.getLong(3),
        )
    } finally {
        statement.close()
    }
}

private fun SQLiteConnection.holdsTable(name: String): Boolean =
    scalarLong("SELECT COUNT(*) FROM `sqlite_master` WHERE `name` = '$name'") > 0L

/**
 * How much of what the user made is in the file — which is not the size of the tables.
 *
 * The chart of accounts holds more than the accounts a person opened: a card is a
 * `LIABILITY` account linked to its facade row, and beyond both sit the system rows the
 * write boundary creates on demand, one set per currency in use, that nothing in the app
 * ever renders. Counting `accounts` would tell someone with one bank account and one card
 * that they have two accounts and one card, and the figure would climb as they spent.
 *
 * `ASSET` is the same line the app draws to decide what an account list shows
 * (`AccountDao`), asked here in raw SQL because this runs over a file on a throwaway
 * connection, with no DAO to ask. Archived accounts are counted: they are in the file and
 * a restore brings them back, and this figure describes the file rather than a screen.
 */
private fun SQLiteConnection.readCounts() = ArchiveCounts(
    accounts = scalarLong("SELECT COUNT(*) FROM `accounts` WHERE `type` = '$USER_ACCOUNT_TYPE'"),
    transactions = scalarLong("SELECT COUNT(*) FROM `transactions`"),
    categories = scalarLong("SELECT COUNT(*) FROM `categories`"),
    creditCards = scalarLong("SELECT COUNT(*) FROM `credit_cards`"),
)

/**
 * A damaged database and a file that is no database at all reach the screen as different
 * sentences, so the one result code that separates them decides. Every other code that
 * gets this far — a path that holds nothing, bytes that are not a database, one this build
 * does not recognise — says the same thing: there is nothing here to read as a database.
 *
 * "That gets this far" is [raiseIfMachineFailure]'s doing, and it is what makes the
 * sentence true rather than merely convenient: a full disk and a refused read never
 * arrive, so the fallback describes the file instead of covering for the device. What
 * does still arrive is the code a missing path answers a read-only open with, and that
 * one belongs here — refusing to open what is not there is the whole reason the flag
 * is set.
 */
private fun SQLiteException.toRejection(): CandidateRejection =
    if (resultCode() == SQLITE_CORRUPT) {
        CandidateRejection.CORRUPTED
    } else {
        CandidateRejection.NOT_A_DATABASE
    }

/**
 * Past layer 3 the bytes have been read as a database and the file has said which app and
 * which schema it belongs to, so what is left is either that claim failing or the machine
 * failing, and only the first is a finding.
 *
 * Pages that stopped adding up between the integrity check and here keep their own name —
 * the screen says "this copy is damaged" for it and nothing else. Every other code lands
 * on [CandidateRejection.SCHEMA_MISMATCH], because on a file that reached this point it
 * means the same thing however SQLite words it: `no such table: entries` and `no such
 * column` are one generic code, and both say the schema the file claimed is not the schema
 * it holds.
 */
private fun SQLiteException.toProvenFileRejection(): CandidateRejection =
    if (resultCode() == SQLITE_CORRUPT) {
        CandidateRejection.CORRUPTED
    } else {
        CandidateRejection.SCHEMA_MISMATCH
    }

/**
 * Leaves, rather than answers, when the refusal is the machine's.
 *
 * Two codes describe the device and say nothing whatever about the file: there is no room
 * left, and the read or the write did not happen. Neither is something a different file
 * would fix, which is the only question a refusal is allowed to leave the user with.
 */
private fun SQLiteException.raiseIfMachineFailure() {
    val resultCode = resultCode()
    if (resultCode == SQLITE_FULL || resultCode == SQLITE_IOERR) {
        throw DatabaseVerificationException(toVerificationError(), this)
    }
}

/**
 * A full disk is 13 and the one condition the user can act on. Everything else keeps its
 * result code and its wording in the cause, where a log wants them.
 */
private fun SQLiteException.toVerificationError(): DatabaseVerificationError =
    if (resultCode() == SQLITE_FULL) {
        DatabaseVerificationError.NO_SPACE
    } else {
        DatabaseVerificationError.UNKNOWN
    }

private const val INTEGRITY_OK = "ok"

/** What an account the user opened is, in the chart that also holds cards and system rows. */
private const val USER_ACCOUNT_TYPE = "ASSET"

/**
 * Room's own bookkeeping table, where it keeps the identity of the schema it wrote. Named
 * once because two places have to agree on it: the layer that reads it as evidence a file
 * came from this app, and the copy that leaves it behind.
 */
internal const val ROOM_MASTER_TABLE = "room_master_table"

private const val STAGE = "candidate verification"
