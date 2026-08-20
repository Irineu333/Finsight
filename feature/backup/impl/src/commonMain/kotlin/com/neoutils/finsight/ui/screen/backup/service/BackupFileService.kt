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
}
