package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowScope
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.VaultAppOpening
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
}
