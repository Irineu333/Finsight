package com.neoutils.finsight.domain.vault

/**
 * How many copies the vault keeps, as the settings sheet offers it (design D10).
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
     * than something they put up with. It is their space either way, and the history
     * screen is where they take a copy out of it by hand.
     */
    EVERYTHING(null),
}

/**
 * How many copies the vault keeps, or null when none are ever removed.
 *
 * The rule has one owner and this is it: whoever is about to sweep reads the limit rather
 * than deciding it again. **The limit does not depend on where the copies are written.**
 * One preference governs both rungs, so the number the sheet shows is the number the sweep
 * enforces — a control over a value the sweep did not read would be a picker that changes
 * nothing, which is worse than no picker.
 */
fun VaultState.copiesKept(): Int? = retention.copies
