package com.neoutils.finsight.domain.vault

/**
 * How many copies a destination the user points at keeps, as the settings sheet offers it
 * (design D10).
 *
 * It counts copies rather than measuring their age, and that is the decision: the space
 * taken is then predictable, it can never reach zero by construction, and it is what a
 * person means when they ask that backups stop piling up. Age would put the number of
 * files at the mercy of how often the app is opened.
 */
enum class BackupRetention(
    /** How many copies survive a sweep, or null when the app removes nothing at all. */
    val copies: Int?,
) {
    FIVE(5),
    TEN(10),
    TWENTY(20),

    /**
     * The user keeps everything, and retention becomes something they switched off rather
     * than something they put up with. It is their folder and their space.
     */
    EVERYTHING(null),
}

/**
 * What the app's own storage keeps, fixed and not the user's to change (design D10).
 *
 * Three, and no setting for it: these are files nobody sees and nobody administers, so a
 * control over them would be configuration without a purpose.
 */
const val COPIES_KEPT_IN_APP_STORAGE = 3

/**
 * How many copies the destination in force keeps, or null when none are ever removed.
 *
 * The rule has one owner and this is it: which limit applies is derived from what the
 * vault is, never decided again by whoever is about to sweep. A screen chooses *whether*
 * the user may set a limit — the app's own storage offers no such choice — and never
 * *which* limit is in force.
 */
fun VaultState.copiesKept(): Int? = when (destination) {
    VaultDestination.APP_STORAGE -> COPIES_KEPT_IN_APP_STORAGE
    VaultDestination.USER_FOLDER -> retention.copies
}
