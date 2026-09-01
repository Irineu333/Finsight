package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.domain.vault.service.FolderLink
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

    @AfterTest
    fun tearDown() {
        chosen.deleteRecursively()
    }

    // ------------------------------------------------------------------- choosing one

    @Test
    fun `nothing has been pointed at until somebody points at something`() = runTest {
        assertEquals(FolderLink.NONE, folder.link())
    }

    @Test
    fun `pointing at a folder links to it directly`() = runTest {
        assertEquals(true, folder.pointAt(chosen).getOrNull())

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
        assertEquals(FolderLink.LINKED, folder.link(), "the same folder is found again")
        assertTrue(chosen.isDirectory)
    }

    @Test
    fun `a folder that is not a directory is refused`() = runTest {
        val notADirectory = File.createTempFile("finsight-not-a-folder", ".txt")
        try {
            assertFalse(
                folder.pointAt(notADirectory).isRight(),
                "a file was accepted as though it were a folder",
            )
        } finally {
            notADirectory.delete()
        }
    }

    // ------------------------------------------------------------------- naming it

    @Test
    fun `there is no name until something is pointed at`() = runTest {
        assertEquals(null, folder.displayPath())
    }

    /**
     * The last segment on its own cannot answer the question the header is asked, and this
     * is that question in the shape somebody actually meets it: two folders called the same
     * thing, in different places, one of which holds their archive.
     */
    @Test
    fun `two folders of the same name read apart`() = runTest {
        val here = File(chosen, "one/Backups").apply { mkdirs() }
        val there = File(chosen, "two/Backups").apply { mkdirs() }

        folder.pointAt(here)
        val first = folder.displayPath()

        folder.pointAt(there)
        val second = folder.displayPath()

        assertEquals("Backups", here.name)
        assertEquals(here.name, there.name, "the two are meant to share a name")
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second, "the header could not tell the two folders apart")
        assertTrue(first.endsWith(File.separator + "one" + File.separator + "Backups"), first)
        assertTrue(second.endsWith(File.separator + "two" + File.separator + "Backups"), second)
    }

    /**
     * A home directory is the person's own name said back to them, and every path they read
     * on this platform is written the shorter way.
     */
    @Test
    fun `a folder under the home directory is written the way a shell writes it`() = runTest {
        val home = File(System.getProperty("user.home"))
        val under = File(home, "finsight-display-path-probe").apply { mkdirs() }

        try {
            folder.pointAt(under)

            assertEquals("~" + File.separator + under.name, folder.displayPath())
        } finally {
            under.deleteRecursively()
        }
    }

    /**
     * A path's name is a property of its text, not a claim about the file system — the same
     * reason [displayPath] costs nothing, unlike [FolderIdentity]. A folder somebody deleted
     * still answers the name it was chosen under; [link] is what says it is gone.
     */
    @Test
    fun `the name still answers after the folder is deleted`() = runTest {
        folder.pointAt(chosen)
        chosen.deleteRecursively()

        assertEquals(chosen.absolutePath, folder.displayPath())
    }
}
