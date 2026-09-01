package com.neoutils.finsight.feature.backup.api

import com.neoutils.finsight.util.UiText

/**
 * Asked by whatever is about to destroy something, so that what it destroys is in a file
 * first (design D6).
 *
 * It is the only way into the vault from outside this feature, and it says nothing about
 * the vault: a caller states the action it is about to perform and is told, by returning,
 * that it may go on. Whether a copy was owed at all, whether the switch is on, whether the
 * copy already there still covers the archive, where copies go and how many are kept are
 * all decided behind this — a caller that knew any of it would be a second place the rules
 * live.
 *
 * There is nothing to call afterwards. A copy taken *after* a deletion records the state
 * already mutilated and must not be presented as protection against it, which is why this
 * contract has one member and it happens before.
 */
fun interface PreventiveBackup {

    /**
     * Returns once [action] may go ahead, having taken the copy that makes it reversible.
     *
     * Returning is not a promise that a copy was written. Nothing is owed when the vault is
     * off (design D1), when [action]'s class is not covered (design D7), or when the copy
     * already in the destination still holds everything the archive does (design D8) — and
     * in all three the action goes ahead, because in all three there is nothing a new file
     * would add.
     *
     * @throws PreventiveCaptureException when a copy was owed and could not be taken. The
     * destructive action must not then happen on this call: only the person told about the
     * failure may say to go on without a copy.
     */
    suspend fun captureBefore(action: DestructiveAction)

    companion object {

        /**
         * Lets every action through and copies nothing. Not a default — the app binds the
         * vault, and a test that silently got this one instead of the real thing would
         * prove the wrong thing. This is for tests whose subject is elsewhere.
         */
        val None = PreventiveBackup { }
    }
}

/**
 * The copy owed before a destructive action could not be taken, so the action has not
 * happened.
 *
 * It refuses by being thrown, for the reason the ledger's own veto does: a refusal a caller
 * may leave unread is not a boundary, and the caller who left it unread would be the one
 * destroying something with nothing behind it. Not catching it aborts the action, which is
 * the outcome the spec asks for by default; catching it is how a screen tells the person
 * [reason] and asks whether to go on without a copy.
 *
 * [reason] is a sentence rather than an error value because the error vocabulary of backup
 * is the translation of what `:core:database` refuses with and lives in this feature's
 * `impl`. A caller has nothing to decide from the value and everything to say from the
 * sentence; [message] carries the same fact in English, for the log.
 */
class PreventiveCaptureException(
    val reason: UiText,
    message: String,
) : Exception(message)
