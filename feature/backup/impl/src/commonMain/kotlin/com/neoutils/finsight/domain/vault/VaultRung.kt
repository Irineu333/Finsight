package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.domain.vault.service.FolderIdentity
import com.neoutils.finsight.domain.vault.service.FolderLink

/**
 * Where the copies are going right now: the rung the person chose, unless the folder it
 * names cannot be reached.
 *
 * **Two facts, and the rule that pairs them, in one place.** The choice is a preference
 * ([VaultState.destination]) and the link is a reading ([VaultFolder.link]); neither answers
 * on its own where a copy lands. Every reader of that answer — the router that sends the
 * operation, the card that names the destination, the screen that lists what is kept — asks
 * this, so none of them can arrive at a different one.
 *
 * **The fall back to the app's own storage is provisional, and it never touches the
 * choice.** Design D12 says a fallen link is announced and answered by the person — point at
 * the folder again, or keep the copies inside the app — and that nothing may be captured
 * into a folder that is not there while the question stands. Writing the preference instead
 * would answer it on their behalf: the way back to an archive the app does not hold is the
 * folder it remembers (design D4), and a fallback that overwrote [chosen] would throw it
 * away. So the choice stays exactly as it was, and [inForce] is the only thing that moves.
 *
 * **It is derived and never stored.** A second preference saying *provisional* would be a
 * second answer that can disagree with the link — stale on the opening where the folder came
 * back, and stale in the other direction on the opening where it went away. There is nothing
 * to keep in step here because there is nothing kept.
 *
 * A link nobody has read yet is [FolderLink.NONE], and it is deliberately not a fallback:
 * until [VaultFolder.check] or [VaultFolder.pointAt] has actually asked, nothing is known
 * against the choice, and the choice stands.
 */
data class VaultRung(
    /** What the person said, as the vault records it. */
    val chosen: VaultDestination,
    /** What the last reading said about the folder they pointed at. */
    val link: FolderLink,
) {

    /**
     * Whether the copies are going somewhere other than where the person said, because the
     * folder they said cannot be reached.
     *
     * Both halves, stated once: a fallen link matters only where the copies are supposed to
     * be going, and a folder left behind by somebody who moved back to the app's own storage
     * is not a fault to report.
     */
    val isProvisional: Boolean
        get() = chosen == VaultDestination.USER_FOLDER && link == FolderLink.BROKEN

    /**
     * The rung an operation actually goes to.
     *
     * It is [chosen] except while [isProvisional], and then it is the one rung that is
     * always there — which is what keeps the app capturing while the question about the
     * folder goes unanswered (design D12: *o app MUST NOT deixar de capturar enquanto
     * espera uma resposta*).
     */
    val inForce: VaultDestination
        get() = if (isProvisional) VaultDestination.APP_STORAGE else chosen
}

/**
 * Which physical place the copies actually go to, right now — [VaultRung.inForce] with the
 * one thing it cannot say: [VaultDestination] has exactly two values, and one of them can
 * name any number of folders. This is *the root* everything in `VaultDestinationChange` and
 * `VaultMigration` used to be missing (see their own comments): a rung that has not moved is
 * not the same claim as a folder that has not moved, and only this can tell the two apart.
 *
 * **Equality is the whole of what this is for**, which plain [Any.equals] already gives it
 * correctly, on one condition its only constructor ([VaultFolder.location]) keeps: [folder]
 * is null exactly when [destination] is [VaultDestination.APP_STORAGE] — the one place this
 * app always keeps, so two locations naming it are equal by [destination] alone, with nothing
 * further for [folder] to disagree about.
 *
 * **It is a value taken at an instant, never held.** [VaultFolder.location] computes it fresh
 * on every read, for the same reason [VaultRung] is derived rather than stored: a location
 * kept across the very call that moves it would be the call unable to tell before from after.
 */
data class VaultLocation(
    val destination: VaultDestination,
    /**
     * Which folder, when [destination] is [VaultDestination.USER_FOLDER] — see
     * [FolderIdentity]. Null there means nothing is known to be pointed at; null because
     * [destination] is [VaultDestination.APP_STORAGE] means the question does not apply.
     */
    val folder: FolderIdentity?,
)
