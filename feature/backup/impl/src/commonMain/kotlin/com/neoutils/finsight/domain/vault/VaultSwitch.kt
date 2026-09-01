package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Turning the vault on, which is also what takes the copy it does not have yet.
 *
 * **The copy is a property of enabling, not of a screen.** The vault is turned on from the
 * switch on the backup screen and from the offer beside a destructive confirmation, which
 * five sheets across three features carry — and both go through here. A first copy written
 * by either caller would be a rule the other one forgets: correct where it was tested,
 * absent everywhere else. Somebody who has just turned the vault on is not owed *"next
 * time you open the app"*.
 *
 * **Nothing is decided here.** Whether a copy is owed is [BackupVault]'s, and design D8
 * states the owner's condition more precisely than counting files would: a copy that still
 * holds everything the archive does covers it, and taking a second one would write the same
 * file under a newer name. That is also why turning the switch *off* goes through the same
 * call and writes nothing — a vault that is off is the first thing
 * [BackupVault.captureIfNeeded] reads (design D1), so there is no second rule here saying
 * so.
 *
 * **The switch moves before the copy does.** The preference is written before the first
 * suspension point, so a caller starting this on the main dispatcher has the state flow
 * carrying the new value before the call returns; the capture is a `VACUUM INTO` of the
 * whole archive and runs off whatever asked for it, exactly as the periodic trigger's does.
 * Nobody waits on disk work to see a toggle move.
 *
 * And it finishes even when what asked for it does not: the screen's switch flips and the
 * person may leave at once, taking the view model's scope with them, while the copy is the
 * whole point of having flipped it.
 */
class VaultSwitch(
    private val state: BackupVaultRepository,
    private val vault: BackupVault,
) {

    /**
     * Writes the switch, then asks the vault for a copy and says what came of it.
     *
     * The outcome is the caller's to state or to leave: a failure means the vault is on and
     * holding nothing, which is worth a word where somebody is looking at the switch they
     * just moved, while a deletion that is about to ask the vault for the same copy says it
     * itself, once, in the sheet that is already asking whether to go on without one.
     */
    suspend fun setOn(isOn: Boolean): CaptureOutcome {
        state.setOn(isOn)

        return withContext(Dispatchers.Default + NonCancellable) { vault.captureIfNeeded() }
    }
}
