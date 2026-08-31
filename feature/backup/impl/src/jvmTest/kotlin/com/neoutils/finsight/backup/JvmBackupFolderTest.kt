package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pointing at a folder on the desktop, and knowing afterwards whether it is still there.
 *
 * It is the whole of design D4's machine except the dialog: what a chosen folder means,
 * what a closed picker means, and the three states the link can be in. A folder chooser
 * cannot be driven by a test on any platform, and it is not where the rules are.
 *
 * **The folders are real, because every claim here is about a file system.** Whether the
 * link survives a restart is a claim about a path written down and read back, and whether a
 * folder that has gone is noticed is a claim about what `isDirectory` answers over one that
 * no longer exists — a fake asked either question would answer whatever the test wanted.
 */
class JvmBackupFolderTest {

    private val chosen: File = Files.createTempDirectory("finsight-chosen").toFile()

    private val settings = MapSettings()

    private val folder = JvmBackupFolder(settings)

    private val own get() = File(chosen, BACKUP_FOLDER_NAME)

    @AfterTest
    fun tearDown() {
        own.deleteRecursively()
        chosen.deleteRecursively()
    }

    // ------------------------------------------------------------------- choosing one

    @Test
    fun `nothing has been pointed at until somebody points at something`() = runTest {
        assertEquals(FolderLink.NONE, folder.link())
    }

    @Test
    fun `pointing at a folder makes the app's own inside it and links to that`() = runTest {
        assertEquals(true, folder.pointAt(chosen).getOrNull())

        assertTrue(own.isDirectory, "the app keeps to a subfolder of its own (design D4)")
        assertEquals(FolderLink.LINKED, folder.link())
    }

    /**
     * A picker somebody closed is not a failure and is not a choice. It leaves the vault
     * exactly as it was, which is what lets the screen say nothing about it.
     */
    @Test
    fun `choosing nothing changes nothing and is not a failure`() = runTest {
        assertEquals(false, folder.pointAt(null).getOrNull())

        assertEquals(FolderLink.NONE, folder.link())
        assertFalse(own.exists(), "nothing was made for a choice nobody made")
    }

    /**
     * The desktop's whole promise: a path is remembered, and it is still a folder after
     * the app has been closed and opened again. Nothing is resolved, refreshed or renewed
     * — which is what task 11.6 means by *without ceremony*.
     */
    @Test
    fun `the folder is still linked after the app is started again`() = runTest {
        folder.pointAt(chosen)

        val afterRestart = JvmBackupFolder(settings)

        assertEquals(FolderLink.LINKED, afterRestart.link())
    }

    // -------------------------------------------------------------- the link falling

    @Test
    fun `a folder that has been deleted is a link that has fallen`() = runTest {
        folder.pointAt(chosen)

        chosen.deleteRecursively()

        assertEquals(FolderLink.BROKEN, folder.link())
    }

    /**
     * The stricter reading, and deliberately so. A chosen folder that is still a directory
     * while the app's own inside it has gone is the shape a detached volume takes as well
     * as the shape of somebody deleting the copies, and neither is repaired without asking
     * (design D9).
     */
    @Test
    fun `the chosen folder standing empty is still a link that has fallen`() = runTest {
        folder.pointAt(chosen)

        own.deleteRecursively()

        assertEquals(FolderLink.BROKEN, folder.link())
        assertTrue(chosen.isDirectory, "the folder the person chose is untouched")
    }

    /**
     * A fallen link is still a link. The path stays written down, because the copies that
     * were written to it are still in it and pointing at the same folder again is the only
     * thing that leads back to them (design D4).
     */
    @Test
    fun `a link that has fallen is never forgotten`() = runTest {
        folder.pointAt(chosen)
        chosen.deleteRecursively()

        folder.link()

        chosen.mkdirs()
        own.mkdirs()
        assertEquals(FolderLink.LINKED, folder.link(), "the same folder is found again")
    }
}
