package com.neoutils.finsight.ui.screen.backup.service

import arrow.core.Either
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext

/**
 * The only part of a backup that lives outside this app's own storage: the file the user
 * points at, and the place the user puts one.
 *
 * Both ends are a copy, and neither direction hands a caller the user's own file. Reading
 * copies in because the verification writes on what it is given — it opens the candidate
 * with Room and lets the whole migration chain run over it — so what it receives has to be
 * a file this app may alter and throw away. Writing copies out because the capture only
 * knows how to write to a path, and on two of the three platforms the destination is not
 * one: Android returns a `content://` and iOS an `NSURL`, and turning either into bytes on
 * disk is the whole of what this service adds.
 *
 * Choosing nothing is not a failure. It arrives as `null` on the way in and `false` on the
 * way out, on the right side of the [Either], because a user who closes a picker has not
 * hit an error and has nothing to be told.
 */
interface BackupFileService {

    /**
     * A private copy of a file the user chose, at a path this app may write to and lose,
     * or null when they chose none.
     */
    suspend fun copyInChosenFile(context: PlatformContext): Either<BackupError, String?>

    /**
     * Hands the file at [sourcePath] to wherever the user chooses to keep it. False when
     * they chose nowhere.
     */
    suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean>

    /**
     * A free path in this app's own temporary area for a capture to be written to.
     *
     * Nothing is left at it: `VACUUM INTO` writes the file itself and refuses a
     * destination that already holds one. The capture cannot be aimed straight at what
     * the user picked — on two of the three platforms that is not a path at all — so
     * every export goes through here first.
     */
    suspend fun newCapturePath(): Either<BackupError, String>

    /**
     * Removes a private copy this service handed out, journal files and all.
     *
     * The journal files are the reason this takes a path rather than a file: a candidate
     * is opened with Room, which runs in write-ahead logging, so a `-wal` and a `-shm`
     * may sit beside it once the verification is done with it.
     *
     * Best effort by design. It is called on the way out of flows that have already
     * failed, and a temporary file that survives is a temporary file — the platform
     * reclaims it — while an exception raised here would replace the failure the caller
     * was in the middle of reporting.
     */
    suspend fun discard(path: String)
}
