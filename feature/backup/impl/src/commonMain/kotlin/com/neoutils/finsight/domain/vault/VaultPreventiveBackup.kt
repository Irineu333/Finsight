package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException

/**
 * The preventive trigger: its own switch, the classification, and the one road from there
 * to the vault.
 *
 * What it adds to [BackupVault] is one occasion and one condition — something is about to
 * be destroyed, and it is the kind of thing worth a copy — and it deliberately adds nothing
 * else. Whether the vault is on, whether the copy already there is still enough, where the
 * file goes and what retention removes are all [BackupVault]'s, so the trigger cannot
 * answer any of them differently from the other two (design D1).
 *
 * The switch read here is the trigger's own, the one the settings sheet puts at the top. It
 * decides *whether* the rule applies and never *which* actions it covers — that is
 * [com.neoutils.finsight.feature.backup.api.DestructiveClass]'s, in the domain, and a
 * screen that could take an action out of it would be a second owner of the rule
 * (design D7).
 *
 * An action whose class is not covered stops here, before the vault is asked for anything:
 * a deletion the domain already refuses when it would cost typed work must not produce a
 * file, and it does not, because nothing downstream is reached.
 */
class VaultPreventiveBackup(
    private val state: BackupVaultRepository,
    private val vault: BackupVault,
) : PreventiveBackup {

    override suspend fun captureBefore(action: DestructiveAction) {
        // A restore is never treated as already covered, and it is the one occasion that
        // is not. Coverage is a claim about a file the vault has not looked at — a copy
        // deleted from a file manager leaves the mark standing (design D9) — and the mark
        // itself is a sum over `sqlite_sequence`, which a session of pure edits does not
        // move at all: closing an invoice, archiving a category, changing a budget. Both
        // gaps are survivable behind a deletion, which takes one row and leaves the rest;
        // behind a replacement of the whole archive they are the difference between a way
        // back and none, and the sheet in front of the person promises the way back.
        take(action) {
            when (action) {
                DestructiveAction.RESTORE_BACKUP -> vault.captureNow()
                else -> vault.captureIfNeeded()
            }
        }
    }

    /**
     * What [ArchiveRestore][com.neoutils.finsight.domain.restore.ArchiveRestore] calls
     * instead of [captureBefore] when the restore is reading from a copy still sitting in
     * the destination, so the sweep this capture triggers leaves [sparing] standing rather
     * than removing the very file the person just chose (see [BackupVault.captureNow]).
     *
     * Everything else — the switch, the classification, the exception a caller has to
     * answer — is the same road [captureBefore] takes for [DestructiveAction.RESTORE_BACKUP];
     * only the copy this one sweep must not touch differs, which is why this is not a second
     * implementation of the trigger.
     */
    suspend fun captureBeforeRestore(sparing: StoredBackup?) {
        take(DestructiveAction.RESTORE_BACKUP) { vault.captureNow(sparing = sparing) }
    }

    private suspend fun take(action: DestructiveAction, capture: suspend () -> CaptureOutcome) {
        if (!state.observe().value.isPreventiveOn) return
        if (!action.classification.isCoveredByPreventiveCapture) return

        when (val outcome = capture()) {
            // A copy landed, the vault is off, or the one in the destination still holds
            // everything the archive does. The action goes ahead in all three: in none of
            // them would another file protect anything — and a restore never reaches the
            // third.
            is CaptureOutcome.Captured,
            CaptureOutcome.VaultOff,
            CaptureOutcome.AlreadyCovered -> Unit

            is CaptureOutcome.Failed -> throw PreventiveCaptureException(
                reason = outcome.error.toUiText(),
                message = outcome.error.message,
            )
        }
    }
}
