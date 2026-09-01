package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.isDesktop

/**
 * What the copies on a rung survive, and therefore which sentence a screen is entitled to
 * say about it.
 *
 * **It is a fact about the destination, and it is derived here rather than read off
 * [VaultDestination] in a `when` beside a string.** Two screens say this — the card on the
 * backup screen and the sheet that chooses between the rungs — and a rung has three
 * truths to tell across the platforms this app ships on, not two. A screen picking a
 * sentence straight from the enum can only ever say two of them, and the one it drops is
 * the one the app's own code contradicts.
 *
 * **The desktop is why there are three.** `~/.finance/` is the app's own storage and it
 * also outlives the app — there is no uninstall that empties a home directory
 * ([com.neoutils.finsight.backup.service.JvmBackupDestination], design D3: *no desktop os
 * dois degraus coincidem*) — so a sentence saying that uninstalling takes the copies with
 * it is false there. It errs towards saying somebody is less protected than they are,
 * which is the gentler direction to be wrong in and still a false claim about their
 * backups, on the one screen whose whole point is not to produce confidence without
 * backing.
 *
 * **This is the platform being read, and it is not the platform being guessed at.**
 * Design D16 refuses to branch the *folder* sentence by platform, because what a folder a
 * person pointed at covers depends on a provider the app cannot ask about and would be
 * wrong in both directions. Nothing of that kind is happening here: whether the app's own
 * storage survives its own uninstall is a property of the storage this app writes to, it
 * is stated in that destination's own file, and it is known at compile time. The sentence
 * is still said per destination — there are three destinations across three platforms,
 * and [VaultDestination] can only name two of them.
 */
enum class VaultCoverage {

    /**
     * The app's own storage on a mobile platform: this device, inside the app, and gone
     * when the app goes.
     */
    INSIDE_THE_APP,

    /**
     * The app's own storage on the desktop, which is a folder in the user's home
     * directory and outlives the app without anybody pointing at anything.
     */
    APP_FOLDER_ON_DESKTOP,

    /** A folder the person pointed at, which outlives the app and can stop being reachable. */
    CHOSEN_FOLDER,
}

/**
 * What [this] rung protects, for whoever has to say it.
 *
 * The one place the desktop exception lives, so that a screen consuming it cannot forget
 * the platform and a second screen cannot remember it differently.
 */
val VaultDestination.coverage: VaultCoverage
    get() = when (this) {
        VaultDestination.APP_STORAGE ->
            if (isDesktop) VaultCoverage.APP_FOLDER_ON_DESKTOP else VaultCoverage.INSIDE_THE_APP

        VaultDestination.USER_FOLDER -> VaultCoverage.CHOSEN_FOLDER
    }
