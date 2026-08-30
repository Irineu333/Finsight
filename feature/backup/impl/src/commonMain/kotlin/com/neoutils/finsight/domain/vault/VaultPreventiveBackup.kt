package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.toUiText
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
        if (!state.observe().value.isPreventiveOn) return
        if (!action.classification.isCoveredByPreventiveCapture) return

        when (val outcome = vault.captureIfNeeded()) {
            // A copy landed, the vault is off, or the one in the destination still holds
            // everything the archive does. The action goes ahead in all three: in none of
            // them would another file protect anything.
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
