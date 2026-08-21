package com.neoutils.finsight.ui.screen.backup.service

import kotlinx.datetime.LocalDate

/**
 * The name an exported file is offered under, dated by the day it was taken.
 *
 * The date is written as [LocalDate] writes itself, which is ISO-8601 and therefore zero
 * padded. That is the property the name is chosen for: backups pile up in one folder, and
 * a padded date sorts them by age with no help from anything else, while `2026-1-5` would
 * file itself between October and November.
 *
 * The extension is `.db` because the file is a SQLite database and nothing more. An
 * extension of this app's own would have to be declared to the system on iOS and would buy
 * nothing: what a candidate file is gets decided by reading it, never by its name.
 */
fun backupFileName(day: LocalDate): String = "finsight-backup-$day.db"
