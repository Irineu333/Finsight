@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The periodic trigger: its own switch, its own question about time, and the same road to
 * the vault the other two take.
 *
 * What it adds to [BackupVault] is one occasion and one condition — the app was opened, and
 * the interval has run out — and it deliberately adds nothing else. Whether the vault is on
 * is not asked here (design D1): a trigger that checked would be a second place the switch
 * is read and a second place it could be read differently. Neither is whether a copy is
 * owed: an interval that has run out with nothing entered since produces no file, and that
 * rule belongs to the vault (design D8), which is why somebody who opens the app every day
 * without entering anything accumulates nothing however long they keep it up.
 *
 * **One opening is one question, and that is the whole of "the first opening after N
 * days"** (design D5). Nothing here counts intervals that went by, so an app closed for
 * months produces one copy when it is opened again — the elapsed time is a condition, never
 * a backlog. It is also why there is no scheduler in sight: what runs, runs while somebody
 * is using the app.
 *
 * **It does not compete with the app opening.** The capture is moved off whatever called it
 * — a `VACUUM INTO` of the whole archive is disk work of the archive's own size, and the
 * caller is a composition — and nothing waits for the result, so the app is usable from the
 * first frame whether this takes a second or fails outright. The outcome is dropped
 * deliberately: there is nobody to tell and nothing to decide, and a failure shows up where
 * it matters, as the instant of the last successful copy not moving (design D12).
 */
class VaultPeriodicBackup(
    private val state: BackupVaultRepository,
    private val vault: BackupVault,
    private val clock: Clock,
) : PeriodicBackup {

    override suspend fun captureIfDue() {
        val vaultState = state.observe().value
        if (!vaultState.isPeriodicOn) return
        if (!vaultState.isIntervalDue(clock.now())) return

        withContext(Dispatchers.Default) { vault.captureIfNeeded() }
    }
}
