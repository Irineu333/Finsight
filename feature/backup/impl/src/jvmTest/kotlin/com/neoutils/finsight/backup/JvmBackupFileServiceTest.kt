package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupFileService
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

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
}
