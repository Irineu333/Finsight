package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.feature.backup.api.PeriodicBackup

/**
 * What the opening of the app is for the vault: the link is checked, and then the copy this
 * opening owes is taken if it owes one.
 *
 * The two are one occasion, and the order between them is the point of putting them
 * together. Checking the link only when something is written means finding out that a
 * folder was deleted, unmounted or renamed at the moment a capture fails — with nobody
 * watching, since two of the three triggers fire without anybody asking for anything (task
 * 11.7, design D12). Checking first is also what the rest of the opening depends on: the
 * reading it publishes is what decides where this opening's copy lands ([VaultRung]), and
 * what the screen puts the two ways out beside — point at the folder again, or keep the
 * copies inside the app.
 *
 * **The check is unconditional and the capture is not.** A fallen link is a fact about this
 * install whichever rung is in force, whether the periodic trigger is on, and whether the
 * interval has run out — so it is read before any of [VaultPeriodicBackup]'s conditions are,
 * and the copy this opening may owe is then taken somewhere that exists rather than failing
 * against a folder that does not.
 *
 * **Nothing is decided here, and nothing is written down.** The reading is published; what
 * follows from it is a derivation ([VaultRung]) and a question on the screen. The choice of
 * where somebody's backups live is never changed on their behalf (design D12).
 *
 * **Neither half can stop the app opening.** The check is a reading of the file system and
 * the capture is fired and forgotten; this adds no failure of its own, which is what lets
 * the shell go on calling one method and awaiting nothing.
 *
 * It takes [PeriodicBackup] rather than the class behind it for the same reason it
 * implements it: what this adds to the occasion is one reading, in front of whatever the
 * opening already did.
 *
 * It implements [PeriodicBackup] because that is the contract through which the shell
 * announces the opening. The name is narrower than what the occasion now carries, and it is
 * the shell's to rename — no feature can reach the call site.
 */
class VaultAppOpening(
    private val folder: VaultFolder,
    private val periodic: PeriodicBackup,
) : PeriodicBackup {

    override suspend fun captureIfDue() {
        folder.check()
        periodic.captureIfDue()
    }
}
