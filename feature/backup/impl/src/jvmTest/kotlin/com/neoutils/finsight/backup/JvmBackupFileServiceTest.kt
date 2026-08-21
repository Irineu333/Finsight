package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupFileService
import com.neoutils.finsight.domain.error.BackupError
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Where the desktop build puts a database while it is between the archive and the file
 * the user picked.
 *
 * The window is short and the content is the whole ledger, so the directory is the only
 * thing that can protect it: both flows delete and recreate the file they were given a
 * path to — the capture through `VACUUM INTO`, the restore through an overwriting copy —
 * so whatever permissions the file was created with are gone by the time it holds
 * anything. The `-wal` and `-shm` files Room leaves beside a candidate are not created
 * by this service at all, and the directory is what covers them too.
 */
class JvmBackupFileServiceTest {

    private fun captureDirectory(): Path {
        val path = assertNotNull(
            runBlocking { JvmBackupFileService().newCapturePath().getOrNull() },
            "the service could not offer a path to capture into",
        )
        return File(path).parentFile.toPath()
    }

    @Test
    fun `the directory a capture is written into is closed to every other user`() {
        val directory = captureDirectory()

        // Windows has no POSIX mode, and its per-user `%TEMP%` is closed by an ACL the
        // JDK inherits — there is nothing to assert and nothing to fix there.
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return

        assertEquals(
            "rwx------",
            PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)),
            "on Linux `java.io.tmpdir` is /tmp, shared by every account on the machine",
        )
    }

    /**
     * The squat: a directory under a shared temporary root, at a name this app is known
     * to use, made by somebody else before this app ever runs — with permissions or a
     * symlink of their choosing. `mkdirs` adopts it without a word.
     */
    @Test
    fun `the directory is never one that was already there`() {
        val squatted = File(System.getProperty("java.io.tmpdir"), "finsight-backup")
        val planted = squatted.mkdirs()
        try {
            assertNotEquals(
                squatted.toPath(),
                captureDirectory(),
                "the archive went into a directory this app did not create",
            )
        } finally {
            if (planted) squatted.delete()
        }
    }

    /**
     * A file is made before it is copied into, so a copy that fails is a file nobody
     * wanted.
     *
     * And the word it fails with is not about the file the user picked: nothing here
     * opened it, so nothing here may say what it is. A copy that did not happen is a
     * check that did not start.
     */
    @Test
    fun `a copy that fails leaves nothing behind`() = runBlocking {
        val service = JvmBackupFileService()
        val directory = captureDirectory()
        val before = names(directory)

        val outcome = service.copyIntoPrivateFile(File(sources(), "not-a-file.db"))

        assertNull(outcome.getOrNull(), "there was nothing to copy")
        assertEquals(
            BackupError.VERIFICATION_FAILED,
            outcome.leftOrNull(),
            "a copy nobody could make says nothing about what it was going to copy",
        )
        assertEquals(before, names(directory), "the copy that did not happen took its file with it")
    }

    /**
     * The narrow way the path is lost: the copy is a blocking call that no cancellation
     * reaches, so it runs to the end and `withContext` raises the cancellation *instead of*
     * returning. The file exists, and the one caller who could have removed it was never
     * told where it is.
     *
     * The source is a fifo, so the copy is held inside the read rather than raced against:
     * opening the write end returns only once the copy has opened the read end, and nothing
     * is written until the cancellation is in. Windows has no fifo and this has nothing to
     * say there.
     */
    @Test
    fun `a copy the caller never receives is removed`() = runBlocking {
        val source = fifo() ?: return@runBlocking
        val service = JvmBackupFileService()
        val directory = captureDirectory()
        val before = names(directory)

        val copy = launch(Dispatchers.IO) { service.copyIntoPrivateFile(source) }
        val writer = withContext(Dispatchers.IO) { FileOutputStream(source) }

        copy.cancel()
        withContext(Dispatchers.IO) { writer.use { it.write(ByteArray(8)) } }
        copy.join()

        assertEquals(
            before,
            names(directory),
            "a copy nobody was handed is a copy nobody can close",
        )
    }

    /** What the private directory holds, as the only way to name files it minted itself. */
    private fun names(directory: Path): Set<String> = directory.toFile().list().orEmpty().toSet()

    /** A directory of this test's own, so a source never lands where the service works. */
    private fun sources(): File = Files.createTempDirectory("finsight-backup-source").toFile()
        .also { it.deleteOnExit() }

    /**
     * A pipe with a name, made by the tool that makes them — the JDK has no call for it —
     * or null where the platform has none.
     */
    private fun fifo(): File? {
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return null
        val path = File(sources(), "fifo")
        val made = ProcessBuilder("mkfifo", path.absolutePath).start().waitFor() == 0
        return path.takeIf { made }?.also { it.deleteOnExit() }
    }
}
