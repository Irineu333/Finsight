package com.neoutils.finsight.feature.backup.api

/**
 * Asked by a confirmation that is about to say what its action costs, so that what it says
 * is what the app will actually do.
 *
 * A sheet that warns an action cannot be undone while the app is about to copy the archive
 * first is a false statement in the UI, and the answer is the one the restore confirmation
 * already got: a sentence that stopped being true is rewritten, not left standing.
 *
 * **A confirmation asks whether a copy will be taken for its own action; it never decides
 * which actions are worth one.** Both halves of the answer are behind this — the vault's
 * switches and [DestructiveClass] — so no screen carries a list of the covered actions, and
 * an action moved between classes changes what every sheet says with nothing else touched
 * (design D7).
 *
 * It is the question and never the copy. Taking one is [PreventiveBackup]'s, asked for by
 * the domain the action goes through, and a screen that could take a copy here would be a
 * second occasion the vault never agreed to.
 */
fun interface PreventiveCoverage {

    /**
     * Whether a copy of the archive is genuinely kept before [action] happens — the vault
     * on, its preventive trigger on, and [action] in a class that is covered.
     *
     * A true answer is a promise made to the person standing in front of the sheet, so it
     * is false wherever any one of the three is missing.
     */
    fun keepsCopyBefore(action: DestructiveAction): Boolean

    companion object {

        /**
         * Keeps nothing before anything, which is what a vault that is off does. Not a
         * default — the app binds the vault — but what a test whose subject is the deletion
         * rather than the sentence hands to it.
         */
        val None = PreventiveCoverage { false }
    }
}
