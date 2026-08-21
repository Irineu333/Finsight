package com.neoutils.finsight.domain.error

import com.neoutils.finsight.database.exception.DatabaseCaptureError
import com.neoutils.finsight.database.exception.DatabaseRestoreError
import com.neoutils.finsight.database.exception.DatabaseVerificationError
import com.neoutils.finsight.database.snapshot.CandidateRejection
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_error_app_out_of_date
import com.neoutils.finsight.resources.backup_error_export_failed
import com.neoutils.finsight.resources.backup_error_file_damaged
import com.neoutils.finsight.resources.backup_error_no_space
import com.neoutils.finsight.resources.backup_error_not_a_backup
import com.neoutils.finsight.resources.backup_error_restore_failed
import com.neoutils.finsight.resources.backup_error_verification_failed
import com.neoutils.finsight.util.UiText

/**
 * Why a backup could not be written or read back, in the terms the person in front of the
 * screen has to answer in.
 *
 * It is not a mirror of what `:core:database` reports, and that is the point of it being
 * here. The database tells ten kinds of candidate and ten kinds of machine failure apart
 * because it is describing a file; this enum groups them by the single thing the user can
 * do next, because it is describing a way out. Two refusals that lead to the same next
 * step are one error here, and the finer finding is not lost — it stays in the message
 * `:core:database` writes on each of its own causes, which is where a log wants it and
 * where the spec keeps it away from the user.
 *
 * It lives in the feature rather than beside the fifteen error types of `:core:model`, and
 * not by preference: it names the refusals of `:core:database`, which `:core:model` cannot
 * see — the dependency runs the other way, and reversing it would close a cycle.
 *
 * The message is English and meant for the log; [toUiText] is what the screen shows.
 */
enum class BackupError(val message: String) {

    /**
     * The file cannot be read as a backup of this app, whatever else it is: not a
     * database, a database of some other program, one this app's schema does not
     * recognise, or one whose contents break an invariant of the ledger.
     *
     * The eight refusals behind this value land on one sentence because the user acts on
     * all eight identically — pick a different file — and because the accounting ones may
     * not say more than that: a person who is told an entry does not sum to zero has been
     * handed a fact about double-entry bookkeeping instead of a way forward.
     */
    NOT_A_BACKUP("The chosen file cannot be read as a backup of this app"),

    /**
     * The file is a database and its pages no longer add up.
     *
     * Kept apart from [NOT_A_BACKUP], and the distinction earns its keep: it is the one
     * case where choosing the same file again is guaranteed to fail. "This is not a
     * backup" invites the user to look again at what they picked, which is often the fix;
     * "this backup is damaged" tells them this particular copy is beyond use and another
     * one is what they need. `:core:database` already spends a SQLite result code to draw
     * exactly this line, so honouring it here costs nothing and dropping it would waste
     * what was measured.
     */
    FILE_DAMAGED("The chosen file is a database whose pages are damaged"),

    /**
     * The file declares a schema newer than this build can open — a backup taken on an
     * up-to-date install and brought to one that is behind.
     *
     * It has a message of its own because the cause is this app, not the file: the file is
     * a perfectly good backup, and the spec is explicit that presenting it as invalid
     * sends the user hunting for a problem that is not there. The way out is updating the
     * app, and nothing else the user does to the file will help.
     */
    APP_OUT_OF_DATE("The file was written by a newer version of this app"),

    /**
     * There is not enough free space for the file being written.
     *
     * One value for both directions — the export writing the capture and the restore
     * writing the replaced content — because freeing space is the whole of the answer in
     * either, and the screen the message appears on already says which one was running.
     */
    NO_SPACE("There is not enough free space to write the file"),

    /**
     * The export did not produce its file, for a reason the user did not cause and cannot
     * remove: the destination was refused, the database was busy, or SQLite failed in a
     * way this app does not recognise. Nothing was written.
     */
    EXPORT_FAILED("The backup file could not be written"),

    /**
     * The restore did not go through. The archive is exactly as it was — the replacement
     * runs in one transaction, so it either takes on the whole file or reverts — and
     * saying so is the point of the message: the operation is irreversible, and a failure
     * with no word about the archive reads as one.
     */
    RESTORE_FAILED("The archive could not be replaced with the file's content"),

    /**
     * The file was never checked, so nothing is known about it and nothing was replaced.
     *
     * It is not [NOT_A_BACKUP] and the distinction is the reason it exists: a verification
     * that could not run has found nothing about the file, and saying it is not a backup
     * sends the user hunting through their files for a fault that is in the machine. It is
     * not [RESTORE_FAILED] either — that word promises the archive is unchanged after an
     * attempt on it, and here there was no attempt.
     */
    VERIFICATION_FAILED("The chosen file could not be checked"),
}

fun BackupError.toUiText(): UiText = when (this) {
    BackupError.NOT_A_BACKUP -> UiText.Res(Res.string.backup_error_not_a_backup)
    BackupError.FILE_DAMAGED -> UiText.Res(Res.string.backup_error_file_damaged)
    BackupError.APP_OUT_OF_DATE -> UiText.Res(Res.string.backup_error_app_out_of_date)
    BackupError.NO_SPACE -> UiText.Res(Res.string.backup_error_no_space)
    BackupError.EXPORT_FAILED -> UiText.Res(Res.string.backup_error_export_failed)
    BackupError.RESTORE_FAILED -> UiText.Res(Res.string.backup_error_restore_failed)
    BackupError.VERIFICATION_FAILED -> UiText.Res(Res.string.backup_error_verification_failed)
}

/**
 * Why a candidate file was turned away, as the restore screen says it.
 *
 * The `when` is exhaustive on purpose: a refusal added to [CandidateRejection] fails to
 * compile here until someone decides what the user is supposed to do about it.
 */
fun CandidateRejection.toBackupError(): BackupError = when (this) {
    CandidateRejection.NOT_A_DATABASE -> BackupError.NOT_A_BACKUP
    CandidateRejection.CORRUPTED -> BackupError.FILE_DAMAGED
    CandidateRejection.NOT_FROM_THIS_APP -> BackupError.NOT_A_BACKUP
    CandidateRejection.SCHEMA_TOO_NEW -> BackupError.APP_OUT_OF_DATE
    CandidateRejection.SCHEMA_MISMATCH -> BackupError.NOT_A_BACKUP
    CandidateRejection.MIGRATION_ABORTED -> BackupError.NOT_A_BACKUP
    CandidateRejection.UNBALANCED_LEDGER -> BackupError.NOT_A_BACKUP
    CandidateRejection.ORPHAN_DIMENSION -> BackupError.NOT_A_BACKUP
    CandidateRejection.FOREIGN_KEY_VIOLATION -> BackupError.NOT_A_BACKUP
    CandidateRejection.MISPLACED_DIMENSION -> BackupError.NOT_A_BACKUP
}

/** Why the export produced no file. */
fun DatabaseCaptureError.toBackupError(): BackupError = when (this) {
    DatabaseCaptureError.NO_SPACE -> BackupError.NO_SPACE
    DatabaseCaptureError.DESTINATION_EXISTS -> BackupError.EXPORT_FAILED
    DatabaseCaptureError.STATEMENT_IN_PROGRESS -> BackupError.EXPORT_FAILED
    DatabaseCaptureError.UNKNOWN -> BackupError.EXPORT_FAILED
}

/** Why the archive was not replaced, over a file that had already been approved. */
fun DatabaseRestoreError.toBackupError(): BackupError = when (this) {
    DatabaseRestoreError.NO_SPACE -> BackupError.NO_SPACE
    DatabaseRestoreError.FOREIGN_KEYS_DISABLED -> BackupError.RESTORE_FAILED
    DatabaseRestoreError.CYCLIC_FOREIGN_KEYS -> BackupError.RESTORE_FAILED
    DatabaseRestoreError.UNKNOWN -> BackupError.RESTORE_FAILED
}

/**
 * Why the gate reached no verdict at all — which is not a verdict against the file, and is
 * the whole reason `:core:database` raises this instead of refusing.
 */
fun DatabaseVerificationError.toBackupError(): BackupError = when (this) {
    DatabaseVerificationError.NO_SPACE -> BackupError.NO_SPACE
    DatabaseVerificationError.UNKNOWN -> BackupError.VERIFICATION_FAILED
}
