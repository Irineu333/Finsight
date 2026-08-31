@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowScope
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.ArchiveCopy
import com.neoutils.finsight.domain.vault.VaultAppOpening
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderIdentity
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.folderIdentity
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The machine that points at a folder, and what follows from each of its answers.
 *
 * What is under test is the *following from*, which is where a half-built rung does damage:
 * a picker somebody closed must leave the vault where it was, a failure must not move it
 * either, and moving back inside the app must remove nothing and forget nothing — because
 * the copies in the folder are still in it and the remembered folder is the only thing that
 * leads back to them (design D4).
 *
 * The folder itself is a stand-in here and a real one in `JvmBackupFolderTest`: what a path
 * on disk does is that test's subject, and what the app does with the three answers is this
 * one's.
 */
class VaultFolderTest {

    private val settings = MapSettings()

    private val state = BackupVaultRepository(settings)

    private var answer: Either<BackupError, Boolean> = true.right()

    private var linked: FolderLink = FolderLink.NONE

    private var timesPointed = 0

    private val stub = object : BackupFolder {

        override val isOffered = true

        override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> {
            timesPointed++
            return answer.onRight { chosen -> if (chosen) linked = FolderLink.LINKED }
        }

        override suspend fun link(): FolderLink = linked

        // The stub only ever plays one folder, so a constant identity whenever something
        // has been pointed at is the whole of the truth it can tell — this file's own
        // tests never switch between two different ones; `FolderRecoveryTest` does, over a
        // real path.
        override val identity: FolderIdentity?
            get() = if (linked == FolderLink.NONE) null else folderIdentity("stub-folder")
    }

    private val folder = VaultFolder(state = state, folder = stub)

    /**
     * A context is asked for by the contract and never read by anything under test: the
     * picker is the one half that needs a window, and it is not this half.
     */
    private val context = PlatformContext(
        object : WindowScope {
            override val window: ComposeWindow get() = error("no picker is raised here")
        }
    )

    private val destination get() = state.observe().value.destination

    /** A vault holding a copy that covers the archive, taken while the rung was in force. */
    private fun coverTheArchive() = state.recordCapture(
        at = COVERED_AT,
        mark = 42,
        copy = ArchiveCopy(name = "finsight-backup-2026-08-30T14-30-05.db", savedAt = COVERED_AT),
    )

    // ----------------------------------------------------------------- choosing a folder

    @Test
    fun `a folder that was pointed at is where the copies go`() = runTest {
        assertEquals(true, folder.pointAt(context).getOrNull())

        assertEquals(VaultDestination.USER_FOLDER, destination)
        assertEquals(FolderLink.LINKED, folder.link.value)
    }

    @Test
    fun `a picker somebody closed leaves the vault exactly as it was`() = runTest {
        answer = false.right()

        assertEquals(false, folder.pointAt(context).getOrNull())

        assertEquals(VaultDestination.APP_STORAGE, destination)
    }

    /**
     * A folder the app could not prepare is not a folder to point the vault at: the copies
     * would stop landing anywhere at the next trigger, and the person would find out from
     * the one line that says when the last one succeeded.
     */
    @Test
    fun `a folder that could not be prepared never becomes the destination`() = runTest {
        answer = BackupError.EXPORT_FAILED.left()

        assertTrue(folder.pointAt(context).isLeft())

        assertEquals(VaultDestination.APP_STORAGE, destination)
    }

    // ------------------------------------------------------------ moving back and forth

    @Test
    fun `keeping copies inside the app forgets neither the folder nor its copies`() = runTest {
        folder.pointAt(context)

        folder.keepInsideApp()

        assertEquals(VaultDestination.APP_STORAGE, destination)
        assertEquals(FolderLink.LINKED, stub.link(), "the folder is still there and still known")

        folder.pointAt(context)
        assertEquals(VaultDestination.USER_FOLDER, destination, "and it is reachable again")
    }

    // ---------------------------------------------------------- the link, when it falls

    @Test
    fun `a link that has fallen is published and nothing is moved because of it`() = runTest {
        folder.pointAt(context)
        linked = FolderLink.BROKEN

        folder.check()

        assertEquals(FolderLink.BROKEN, folder.link.value)
        assertEquals(
            VaultDestination.USER_FOLDER,
            destination,
            "the app moved somebody's backups without asking",
        )
    }

    /**
     * Task 11.7: the link is read when the app opens, not when something is written. The
     * periodic trigger is off here, so nothing was ever going to write — and the fallen
     * link is noticed anyway, which is the whole point of asking at the opening.
     */
    @Test
    fun `the app opening reads the link even when nothing would be captured`() = runTest {
        folder.pointAt(context)
        linked = FolderLink.BROKEN
        state.setPeriodicOn(false)

        VaultAppOpening(folder = folder, periodic = PeriodicBackup.None).captureIfDue()

        assertEquals(FolderLink.BROKEN, folder.link.value)
    }

    @Test
    fun `the app opening still lets the periodic trigger run`() = runTest {
        var captured = false

        VaultAppOpening(folder = folder, periodic = { captured = true }).captureIfDue()

        assertTrue(captured, "checking the link replaced the trigger instead of preceding it")
    }

    // ------------------------------------------ coverage is a claim about a place, not a file

    /**
     * The one thing a restore confirmation may not get wrong. Coverage says *a copy holds
     * everything the archive holds*, so the preventive trigger takes none — and the sheet
     * promises the person finds that copy in the kept copies screen, which lists the rung in
     * force. A claim carried across a change of rung is a promise about a copy in the rung
     * left behind.
     */
    @Test
    fun `pointing at a folder gives up a coverage that was taken inside the app`() = runTest {
        coverTheArchive()

        folder.pointAt(context)

        assertNull(
            state.observe().value.markAtLastCapture,
            "a copy inside the app was left covering an archive whose copies go to a folder",
        )
        assertNull(state.observe().value.archiveCopy, "and it was left named on the new rung")
        assertEquals(COVERED_AT, state.observe().value.lastCapturedAt, "the capture still happened")
    }

    @Test
    fun `moving back inside the app gives up a coverage that was taken in the folder`() = runTest {
        folder.pointAt(context)
        coverTheArchive()

        folder.keepInsideApp()

        assertNull(state.observe().value.markAtLastCapture)
    }

    /**
     * The link falling moves the rung without anybody choosing to (design D12), so the copy
     * that covers stays in a folder the app cannot reach while the copies land inside it.
     */
    @Test
    fun `a link that falls gives up the coverage it left in the folder`() = runTest {
        folder.pointAt(context)
        coverTheArchive()
        linked = FolderLink.BROKEN

        folder.check()

        assertNull(state.observe().value.markAtLastCapture)
    }

    @Test
    fun `a link that comes back gives up the coverage taken while it was down`() = runTest {
        folder.pointAt(context)
        linked = FolderLink.BROKEN
        folder.check()
        coverTheArchive()

        linked = FolderLink.LINKED
        folder.check()

        assertNull(state.observe().value.markAtLastCapture)
    }

    /**
     * Nothing moved, so nothing is given up: while the folder cannot be reached the copies
     * are already going inside the app, and answering the question with *keep them there*
     * leaves them landing exactly where they were.
     */
    @Test
    fun `a rung that does not move keeps the coverage it has`() = runTest {
        folder.pointAt(context)
        linked = FolderLink.BROKEN
        folder.check()
        coverTheArchive()

        folder.keepInsideApp()

        assertNotNull(
            state.observe().value.markAtLastCapture,
            "a copy that still covers was thrown away by a rung that never moved",
        )
    }

    @Test
    fun `checking a link that has not changed keeps the coverage`() = runTest {
        folder.pointAt(context)
        coverTheArchive()

        folder.check()

        assertNotNull(state.observe().value.markAtLastCapture)
    }

    private companion object {
        val COVERED_AT: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}
