package com.neoutils.finsight.backup

import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

/**
 * The name a captured file is written under.
 *
 * Zero padding is the whole of what is worth asserting about the stamp: the name is what
 * the user will see in a folder alongside every other backup they have taken, and a stamp
 * that pads sorts by age on its own. `2026-1-5` would file itself between October and
 * November, which is exactly the moment someone reaches for the wrong copy.
 *
 * The time is asserted because it is what a destination the app writes to on its own
 * depends on: a day was enough while a save dialog asked about replacing, and a vault has
 * nobody to ask.
 */
class BackupFileNameTest {

    @Test
    fun `the name carries the moment it was taken`() {
        assertEquals(
            "finsight-backup-2026-08-20T14-30-05.db",
            backupFileName(LocalDateTime(2026, 8, 20, 14, 30, 5)),
        )
    }

    @Test
    fun `single-digit months, days, hours, minutes and seconds are padded`() {
        assertEquals(
            "finsight-backup-2026-01-05T09-07-03.db",
            backupFileName(LocalDateTime(2026, 1, 5, 9, 7, 3)),
            "an unpadded stamp sorts January the 5th between October and November",
        )
    }

    /**
     * Two copies on the same day were one name while the granularity was the day, and one
     * of them would have replaced the other in a destination nobody is asked about.
     */
    @Test
    fun `two copies taken on the same day are two names`() {
        val morning = backupFileName(LocalDateTime(2026, 8, 20, 9, 0, 0))
        val evening = backupFileName(LocalDateTime(2026, 8, 20, 21, 0, 0))

        assertTrue(morning < evening, "the earlier copy sorts before the later one")
    }

    @Test
    fun `a name this app wrote is recognised, and anything else is not`() {
        assertTrue(isBackupFileName(backupFileName(LocalDateTime(2026, 8, 20, 14, 30, 5))))
        assertTrue(
            isBackupFileName("finsight-backup-2026-08-20T14-30-05 (1).db"),
            "a provider that renames to avoid a clash still hands back this app's copy",
        )
        assertFalse(isBackupFileName("notes.txt"))
        assertFalse(isBackupFileName("finsight-backup-2026-08-20T14-30-05.db-wal"))
        assertFalse(isBackupFileName("some-other-app.db"))
    }

    @Test
    fun `a name already taken is stepped past, and the free one keeps the extension`() {
        val taken = setOf(
            "finsight-backup-2026-08-20T14-30-05.db",
            "finsight-backup-2026-08-20T14-30-05-2.db",
        )

        val free = freeBackupFileName("finsight-backup-2026-08-20T14-30-05.db") { it in taken }

        assertEquals("finsight-backup-2026-08-20T14-30-05-3.db", free)
        assertTrue(isBackupFileName(free), "the copy is still one this app will find again")
    }

    @Test
    fun `a name nobody holds is the name that is used`() {
        val name = backupFileName(LocalDateTime(2026, 8, 20, 14, 30, 5))

        assertEquals(name, freeBackupFileName(name) { false })
    }
}
