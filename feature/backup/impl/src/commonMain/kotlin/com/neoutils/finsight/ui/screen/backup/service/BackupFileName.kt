package com.neoutils.finsight.ui.screen.backup.service

import kotlinx.datetime.LocalDateTime

private const val NAME_PREFIX = "finsight-backup-"
private const val NAME_SUFFIX = ".db"

/**
 * What an imported file's name carries that a captured one's does not — the one fact this
 * install actually knows about a copy it did not take itself.
 *
 * Nothing inside a file says which install wrote it (`snapshot_meta` carries a platform, an
 * app version and a stamp — the same four columns whoever captured it,
 * [com.neoutils.finsight.domain.vault.ArchiveImport] and
 * [com.neoutils.finsight.domain.vault.BackupVault] alike), so a file this install merely
 * brought in from a picker is, once it is sitting in the destination, a file that reads
 * exactly like one this install captured itself — unless something records which of the two
 * happened. This is that something: the one moment the distinction is known is the moment
 * the file lands, and [com.neoutils.finsight.domain.vault.ArchiveImport] is the only caller
 * that ever knows it landed this way.
 */
private const val IMPORTED_MARK = "imported-"

/**
 * The name a captured file is written under, dated by the moment it was taken.
 *
 * The date is written as [kotlinx.datetime.LocalDate] writes itself, which is ISO-8601 and
 * therefore zero padded, and the time is padded to match. That is the property the name is
 * chosen for: backups pile up in one folder, and a padded stamp sorts them by age with no
 * help from anything else, while `2026-1-5` would file itself between October and November.
 *
 * The time is there because a destination the app writes to on its own has nobody to ask
 * about replacing a file (design D9). A day was enough while every export went through a
 * save dialog; a second copy taken on the same day would otherwise arrive at a name that
 * is already taken, and the copy it would replace is the one thing nobody wants back.
 *
 * The separators are `-` where a clock would write `:`, because a colon is not a legal
 * character in a file name on every platform this app runs on and the whole point of the
 * name is that it survives being written down.
 *
 * The extension is `.db` because the file is a SQLite database and nothing more. An
 * extension of this app's own would have to be declared to the system on iOS and would buy
 * nothing: what a candidate file is gets decided by reading it, never by its name.
 *
 * @param imported whether the file arrived through
 * [com.neoutils.finsight.domain.vault.ArchiveImport] rather than through a capture of this
 * install's own archive. It is written into the name — see [IMPORTED_MARK] — because
 * nothing else records it once the file is sitting in the destination beside every other
 * copy, and [isImportedFileName] is what a restore reads it back with. It changes nothing
 * about [isBackupFileName], retention or removal, which all read the prefix and the
 * extension and leave what is between them free — an imported copy is counted, dated and
 * swept exactly like any other.
 */
fun backupFileName(at: LocalDateTime, imported: Boolean = false): String = buildString {
    append(NAME_PREFIX)
    if (imported) append(IMPORTED_MARK)
    append(at.date)
    append(TIME_MARK)
    append(at.hour.padded())
    append(TIME_SEPARATOR)
    append(at.minute.padded())
    append(TIME_SEPARATOR)
    append(at.second.padded())
    append(NAME_SUFFIX)
}

/**
 * Whether [name] names a copy [com.neoutils.finsight.domain.vault.ArchiveImport] put in the
 * destination, as opposed to one this install captured itself.
 *
 * It is read, never asserted: a name is not authority over what a file is (design D9), and
 * this answers a question about wording, not about safety — nothing here is trusted to
 * decide what may be removed or restored. What it settles is a single sentence, in
 * [com.neoutils.finsight.domain.restore.RestoreSource]: a copy this install cannot vouch for
 * having taken itself must not be described as this app's own past, whether it was picked
 * from a device or arrived in a folder another install also writes to and was imported from
 * there.
 */
fun isImportedFileName(name: String): Boolean =
    name.startsWith(NAME_PREFIX + IMPORTED_MARK)

/**
 * Whether a file in a destination is worth looking at as a copy of this app's archive.
 *
 * It is a filter and not a verdict, and the difference is the whole of design D9: a file
 * system may hand back a name of its own making — Android's `DocumentsProvider` renames a
 * document to avoid a clash, and a user may rename anything — so the name settles what is
 * listed and never what is removed. What a file is gets decided by reading it.
 *
 * That is also why it asks for so little: the prefix and the extension, with everything
 * between them free. A copy that arrived as `finsight-backup-… (1).db` is still this app's
 * to show and to count.
 */
fun isBackupFileName(name: String): Boolean =
    name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX)

/**
 * The one name the copy taken before a migration is written under.
 *
 * It is a single fixed name and not a dated one, and both halves of that are the decision.
 * There is only ever one such copy: it is replaced by the copy the *next* migration takes
 * and by nothing else (design D10), because the damage it exists to undo is a migration
 * that finished without an error and wrote something wrong, which is found out days later
 * — by which time the periodic captures would have carried a dated copy away.
 *
 * Being outside the dated shape is what keeps it out of retention's count. Retention
 * removes the copies the vault takes on a schedule, so it recognises them by the name it
 * writes them under and leaves everything else alone; the worst a name that does not match
 * can do here is spare a file, never remove one.
 *
 * It still starts with the prefix, so it is listed with the rest — the history is where
 * somebody goes looking when a figure stopped adding up after an update, and a copy that
 * is not listed is a copy nobody restores (design D1).
 */
const val PRE_MIGRATION_BACKUP_NAME: String = "${NAME_PREFIX}migration$NAME_SUFFIX"

/**
 * What is appended to the name a copy is going to have, for as long as it is being written.
 *
 * It goes *after* the extension, and that is the whole of the design: [isBackupFileName]
 * asks for the extension at the end, so a file under a staged name is listed by nothing,
 * counted by nothing and swept by nothing. That is exactly what a file that may still turn
 * out to be half a copy has to be — a truncated database is refused by the content check
 * for good, so one left under a name the app recognises would occupy a place in retention
 * that no capture can ever free.
 */
internal const val STAGED_SUFFIX: String = ".part"

/**
 * The name a pre-migration copy is written under while it is being written.
 *
 * It becomes the reserved name only once it has been read as a database of an older schema,
 * and until then the copy from the last migration is the one in force.
 */
internal const val STAGED_PRE_MIGRATION_NAME: String =
    "$PRE_MIGRATION_BACKUP_NAME$STAGED_SUFFIX"

/**
 * [name], or the first name near it that [isTaken] answers no to.
 *
 * A destination writes without asking, so a name already in use is not a question to put to
 * anybody — it is a copy that must not be overwritten. The suffix is appended before the
 * extension so the file stays a `.db` and stays sortable next to the one it collided with.
 */
internal fun freeBackupFileName(name: String, isTaken: (String) -> Boolean): String {
    if (!isTaken(name)) return name
    val stem = name.removeSuffix(NAME_SUFFIX)
    return generateSequence(2) { it + 1 }
        .map { attempt -> "$stem$TIME_SEPARATOR$attempt$NAME_SUFFIX" }
        .first { !isTaken(it) }
}

private fun Int.padded(): String = toString().padStart(PAD_WIDTH, '0')

/** What ISO-8601 puts between a date and a time, and the one part of it a file name keeps. */
private const val TIME_MARK = 'T'
private const val TIME_SEPARATOR = '-'
private const val PAD_WIDTH = 2
