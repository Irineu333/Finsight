package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage

/**
 * What a confirmation is told about its own action, read off the same vault the trigger
 * reads.
 *
 * It answers with [VaultState.keepsCopyBefore] and nothing of its own, which is what keeps
 * the sentence and the copy from coming apart: a sheet that promised a copy the trigger
 * would not take, or stayed silent about one it will, would be a second owner of the rule
 * (design D7).
 *
 * The snapshot is read when the question is asked. Nothing can change the vault while a
 * confirmation is up except accepting the offer beside it, which happens as the action
 * starts — so a sheet that offered the vault still says what was true when it opened, and
 * the copy taken a moment later is more than it promised rather than less.
 */
class VaultPreventiveCoverage(
    private val state: BackupVaultRepository,
) : PreventiveCoverage {

    override fun keepsCopyBefore(action: DestructiveAction): Boolean =
        state.observe().value.keepsCopyBefore(action)
}
