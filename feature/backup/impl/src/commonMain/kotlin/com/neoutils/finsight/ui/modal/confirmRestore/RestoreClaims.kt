package com.neoutils.finsight.ui.modal.confirmRestore

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreSource
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_age
import com.neoutils.finsight.resources.backup_confirm_file_age
import com.neoutils.finsight.resources.backup_confirm_irreversible
import com.neoutils.finsight.resources.backup_confirm_irreversible_title
import com.neoutils.finsight.resources.backup_confirm_reversible
import com.neoutils.finsight.resources.backup_confirm_reversible_title
import com.neoutils.finsight.resources.backup_history_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which sentences the restore confirmation is entitled to say, worked out from the file and
 * from the vault rather than chosen inside the sheet.
 *
 * It is a value for the reason
 * [com.neoutils.finsight.ui.modal.vaultSettings.VaultOutcome] is: the sheet says different
 * things in different states, there is no way to render one here and read it back, and a
 * claim about somebody's whole financial archive is not something to leave unpinned. Stated
 * as a value, every condition can be put to it directly and the test names the resource the
 * sheet will resolve.
 */

/**
 * The line about how far back the file reaches — or none, on a file that carries no stamp
 * and can therefore be dated at all only by guessing.
 *
 * **The two sources are two different sentences, and that is the point.** A kept copy is this
 * install's own archive at the instant stamped in it, so restoring one really does take the
 * app back to how it was. A picked file is a file: nothing in it names a device, the backup
 * screen's own tile says it normally comes from another one, and "how it was" would then be
 * a state this app was never in. What is left to say about such a file is the fact the stamp
 * actually carries — when it was written.
 */
internal fun ageSentence(confirmation: RestoreConfirmation): StringResource? = when {
    confirmation.origin == null -> null
    confirmation.source == RestoreSource.KEPT_COPY -> Res.string.backup_confirm_age
    else -> Res.string.backup_confirm_file_age
}

/**
 * What is left of the current archive once the replacement has happened.
 *
 * The two states differ in one fact — whether a copy of the archive is genuinely written
 * before it goes — and neither of them says the replacement is anything less than total.
 * The irreversible one deliberately claims no more than that: with the vault on and only the
 * preventive trigger off, copies from the periodic trigger are still in the destination, so
 * a sheet that said the previous state could never be brought back would be wrong about
 * somebody's own backups.
 */
internal enum class RestoreAftermath(
    val title: StringResource,
    val message: StringResource,
) {

    /** A copy of the archive as it stands is written first, and it is reachable afterwards. */
    COPY_KEPT(
        title = Res.string.backup_confirm_reversible_title,
        message = Res.string.backup_confirm_reversible,
    ),

    /** Nothing is written first: the archive is replaced with nothing kept back. */
    NO_COPY(
        title = Res.string.backup_confirm_irreversible_title,
        message = Res.string.backup_confirm_irreversible,
    ),
}

/**
 * @param keepsCopy whether a copy is genuinely kept before *this* restore — the vault's
 * answer for [com.neoutils.finsight.feature.backup.api.DestructiveAction.RESTORE_BACKUP],
 * never a reading this sheet takes of the switches.
 */
internal fun restoreAftermath(keepsCopy: Boolean): RestoreAftermath =
    if (keepsCopy) RestoreAftermath.COPY_KEPT else RestoreAftermath.NO_COPY

/**
 * The sentence under the heading, with the copies screen named by the same key its own title
 * comes from — so the sheet cannot go on pointing at a screen that has been renamed.
 *
 * It is not called `message` for the reason it reads as one: `message` is the resource, this
 * is the resource resolved, and a name that could mean either would be one letter away from
 * printing a placeholder to somebody.
 */
@Composable
internal fun RestoreAftermath.hint(): String = when (this) {
    RestoreAftermath.COPY_KEPT -> stringResource(
        message,
        stringResource(Res.string.backup_history_title),
    )

    RestoreAftermath.NO_COPY -> stringResource(message)
}
