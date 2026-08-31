package com.neoutils.finsight.ui.screen.backup.service

import kotlinx.datetime.LocalDateTime

private const val NAME_PREFIX = "finsight-backup-"
private const val NAME_SUFFIX = ".db"

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
 */
fun backupFileName(at: LocalDateTime): String = buildString {
    append(NAME_PREFIX)
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
