package com.neoutils.finsight.backup

import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/**
 * The name a captured file is offered under.
 *
 * Zero padding is the whole of what is worth asserting: the name is what the user will
 * see in a folder alongside every other backup they have taken, and a date that pads
 * sorts by age on its own. `2026-1-5` would file itself between October and November,
 * which is exactly the moment someone reaches for the wrong copy.
 */
class BackupFileNameTest {

    @Test
    fun `the name carries the day it was taken`() {
        assertEquals(
            "finsight-backup-2026-08-20.db",
            backupFileName(LocalDate(2026, 8, 20)),
        )
    }

    @Test
    fun `single-digit months and days are padded`() {
        assertEquals(
            "finsight-backup-2026-01-05.db",
            backupFileName(LocalDate(2026, 1, 5)),
            "an unpadded date sorts January the 5th between October and November",
        )
    }
}
