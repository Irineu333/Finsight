@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultRung
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.ui.screen.backup.BackupUiState
import com.neoutils.finsight.ui.screen.backup.VaultCopies
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Where the copies go, out of the two facts that decide it: what the person chose, and what
 * the last reading said about the folder they chose.
 *
 * It is a derivation and not a record, so this is where it is pinned: the router, the card
 * and the kept-copies screen all ask this one question, and a second answer anywhere would be
 * a screen naming a destination that nothing is landing in.
 */
class VaultRungTest {

    private fun rung(chosen: VaultDestination, link: FolderLink) = VaultRung(chosen, link)

    @Test
    fun `the app's own storage is never provisional, whatever a folder reading says`() {
        FolderLink.entries.forEach { link ->
            val rung = rung(VaultDestination.APP_STORAGE, link)

            assertFalse(rung.isProvisional, "a folder left behind was reported as a fault")
            assertEquals(VaultDestination.APP_STORAGE, rung.inForce)
        }
    }

    @Test
    fun `a folder that answers goes on receiving the copies`() {
        val rung = rung(VaultDestination.USER_FOLDER, FolderLink.LINKED)

        assertFalse(rung.isProvisional)
        assertEquals(VaultDestination.USER_FOLDER, rung.inForce)
    }

    /**
     * The reading starts at [FolderLink.NONE] and means *nobody has asked yet*. Reading it
     * as a fallen link would send the first copy of every session inside the app, on a
     * folder that is right there.
     */
    @Test
    fun `a link nobody has read yet is not a fallen one`() {
        val rung = rung(VaultDestination.USER_FOLDER, FolderLink.NONE)

        assertFalse(rung.isProvisional)
        assertEquals(VaultDestination.USER_FOLDER, rung.inForce)
    }

    /**
     * Design D12: the copies go on being taken while the question about the folder stands,
     * and the choice is left exactly as the person made it — it is what leads back to the
     * archive the app cannot reach (design D4).
     */
    @Test
    fun `a folder that cannot be reached sends the copies inside the app, and keeps the choice`() {
        val rung = rung(VaultDestination.USER_FOLDER, FolderLink.BROKEN)

        assertTrue(rung.isProvisional)
        assertEquals(VaultDestination.APP_STORAGE, rung.inForce)
        assertEquals(VaultDestination.USER_FOLDER, rung.chosen, "the choice was overwritten")
    }

    // --------------------------------------------------------- what the screen may say

    @Test
    fun `a screen that has read nothing claims nothing`() {
        assertFalse(BackupUiState().copiesInForce.isRead)
    }

    /**
     * The reading that stands after a failed re-read belongs to the rung it was taken from.
     * Kept across a change of rung it is the app's own storage counted under the name of a
     * folder — which is what a fallen link and a change of destination both produce.
     */
    @Test
    fun `a reading of the rung left behind is not shown over the one now in force`() {
        val state = BackupUiState(
            vault = VaultState(destination = VaultDestination.USER_FOLDER),
            folderLink = FolderLink.LINKED,
            copies = VaultCopies(rung = VaultDestination.APP_STORAGE, count = 7),
        )

        assertFalse(state.copiesInForce.isRead, "the count of the other rung was left standing")
        assertEquals(0, state.copiesInForce.count)
        assertEquals(7, state.copies.count, "the reading itself is still there, unshown")
    }

    @Test
    fun `a reading of the rung in force is shown`() {
        val state = BackupUiState(
            vault = VaultState(destination = VaultDestination.USER_FOLDER),
            folderLink = FolderLink.LINKED,
            copies = VaultCopies(rung = VaultDestination.USER_FOLDER, count = 7),
        )

        assertTrue(state.copiesInForce.isRead)
        assertEquals(7, state.copiesInForce.count)
    }

    /**
     * The link falling moves the rung under a reading nobody re-took. The folder's count
     * must not survive the sentence saying the folder cannot be reached.
     */
    @Test
    fun `a folder count does not survive the link falling`() {
        val state = BackupUiState(
            vault = VaultState(destination = VaultDestination.USER_FOLDER),
            folderLink = FolderLink.BROKEN,
            copies = VaultCopies(rung = VaultDestination.USER_FOLDER, count = 7),
        )

        assertTrue(state.rung.isProvisional)
        assertFalse(state.copiesInForce.isRead)
    }

    // ------------------------------------------------- what the card names as the last copy

    /**
     * The instant sits between the destination's name and its count. An instant belonging to
     * the rung left behind is the card saying a copy taken inside the app is in the folder
     * somebody has just pointed at — the same failure [BackupUiState.copiesInForce] catches
     * one line up, in the field beside it.
     */
    @Test
    fun `the card names the newest copy standing where the copies now go`() {
        val state = BackupUiState(
            vault = VaultState(
                destination = VaultDestination.USER_FOLDER,
                lastCapturedAt = CAPTURED_INSIDE_THE_APP,
            ),
            folderLink = FolderLink.LINKED,
            copies = VaultCopies(
                rung = VaultDestination.USER_FOLDER,
                count = 5,
                newestAt = NEWEST_IN_THE_FOLDER,
            ),
        )

        assertEquals(
            NEWEST_IN_THE_FOLDER,
            state.lastCopyAt,
            "the card put an instant from the app's own storage over the folder's count",
        )
    }

    /**
     * A destination that answered and holds nothing is empty, and the card says so — the
     * state a freshly chosen folder is in, where this install's own last capture went
     * somewhere else entirely.
     */
    @Test
    fun `a destination that answered and holds nothing is named as holding nothing`() {
        val state = BackupUiState(
            vault = VaultState(
                destination = VaultDestination.USER_FOLDER,
                lastCapturedAt = CAPTURED_INSIDE_THE_APP,
            ),
            folderLink = FolderLink.LINKED,
            copies = VaultCopies(rung = VaultDestination.USER_FOLDER, count = 0),
        )

        assertNull(state.lastCopyAt, "an empty folder was named with a copy taken elsewhere")
    }

    /**
     * Until a listing lands there is nothing to name but this install's own capture, which is
     * a fact about the install rather than about the destination — and it is replaced the
     * moment the reading arrives.
     */
    @Test
    fun `a destination that has not answered leaves this install's own capture standing`() {
        val state = BackupUiState(vault = VaultState(lastCapturedAt = CAPTURED_INSIDE_THE_APP))

        assertFalse(state.copiesInForce.isRead)
        assertEquals(CAPTURED_INSIDE_THE_APP, state.lastCopyAt)
    }

    private companion object {
        val CAPTURED_INSIDE_THE_APP: Instant = Instant.parse("2026-03-01T04:27:00Z")
        val NEWEST_IN_THE_FOLDER: Instant = Instant.parse("2026-03-08T09:10:00Z")
    }
}
