@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime

/**
 * What decides which copy is the newest, when the destination cannot say.
 *
 * **A destination that reports one time for every file is not a hypothetical.** Android's
 * SAF rung reads `COLUMN_LAST_MODIFIED`, a column a provider is free to keep no value in,
 * and the cursor then answers zero for all of them (`SafDocuments.readChild`). Every copy
 * ties, and the tiebreaker becomes the whole of the order — in the listing the history
 * shows, and in the listing retention counts.
 *
 * **The tiebreaker used to be the raw name, and the raw names are not comparable.** They
 * all open with `finsight-backup-`, so what decided was the next character: `imported-` for
 * a copy brought in through the picker, a year for one this install captured. `i` outranks
 * `2`, and the order is newest first — so every imported copy ranked above a copy taken
 * seconds ago. With retention at five and five imported copies in the folder, the sweep
 * behind a capture reached past all five and removed the file that capture had just
 * written, and the person was told their backup succeeded.
 *
 * That is why these are asked of the comparator rather than of a screen or a sweep: it is
 * the one comparator all six destinations sort with, so what is pinned here is pinned for
 * the SAF rung this can never run on.
 */
class ImportedCopyOrderTest {

    /**
     * The regression, in the shape it actually took: nothing can be told apart by time, and
     * the copy taken last has to come first anyway.
     */
    @Test
    fun `a copy captured after five imported ones is the newest, on a destination that ties`() {
        val imported = (1..5).map { minute ->
            copy(name = backupFileName(at(minute), imported = true), savedAt = TIED)
        }
        val captured = copy(name = backupFileName(at(minute = 30)), savedAt = TIED)

        val order = (imported + captured).sortedWith(NEWEST_FIRST)

        assertEquals(
            captured.name,
            order.first().name,
            "the copy taken last ranked below imported ones, where retention counts first",
        )
    }

    /**
     * The mark is dropped, not inverted. An imported copy that really is the newest still
     * leads — otherwise this would have traded one wrong order for its mirror image, and
     * an import would be the thing retention reached for first.
     */
    @Test
    fun `an imported copy that carries the later stamp still leads`() {
        val captured = copy(name = backupFileName(at(minute = 10)), savedAt = TIED)
        val imported = copy(
            name = backupFileName(at(minute = 40), imported = true),
            savedAt = TIED,
        )

        val order = listOf(captured, imported).sortedWith(NEWEST_FIRST)

        assertEquals(imported.name, order.first().name)
    }

    /**
     * The stamp only ever settles a tie. Where the destination does keep times, what it says
     * decides — the name is not authority over what a file is (design D9), and a stamp is
     * still a name.
     */
    @Test
    fun `a destination that keeps times is believed over the stamp`() {
        val older = copy(name = backupFileName(at(minute = 50)), savedAt = TIED)
        val newer = copy(
            name = backupFileName(at(minute = 5), imported = true),
            savedAt = TIED + ONE_HOUR,
        )

        val order = listOf(older, newer).sortedWith(NEWEST_FIRST)

        assertEquals(newer.name, order.first().name, "the file system's own time lost")
    }

    /**
     * A name carrying no stamp claims nothing about being new.
     *
     * The copy taken before a migration is the one this app writes that way, and a file
     * somebody renamed by hand is the one it does not — the listing's name filter is
     * deliberately loose (design D9), so both are here. Neither may sit above a dated copy
     * on a tie: retention never counts the pre-migration copy, so for that one this is only
     * the list's order, but a renamed file is counted like any other.
     */
    @Test
    fun `a name with no stamp sorts last where nothing else decides`() {
        val dated = copy(name = backupFileName(at(minute = 1)), savedAt = TIED)
        val migration = copy(name = PRE_MIGRATION_BACKUP_NAME, savedAt = TIED)
        val renamed = copy(name = "finsight-backup-zzz.db", savedAt = TIED)

        val order = listOf(migration, renamed, dated).sortedWith(NEWEST_FIRST)

        assertEquals(dated.name, order.first().name, "a stampless name outranked a dated one")
    }

    private fun copy(name: String, savedAt: Instant) = StoredBackup(
        name = name,
        savedAt = savedAt,
        sizeInBytes = 1_024,
    )

    private fun at(minute: Int) = LocalDateTime(2026, 8, 30, 10, minute, 0)

    private companion object {

        /** What a provider keeping no modification time answers, for every file it holds. */
        val TIED: Instant = Instant.fromEpochMilliseconds(0)

        val ONE_HOUR = kotlin.time.Duration.parse("1h")
    }
}
