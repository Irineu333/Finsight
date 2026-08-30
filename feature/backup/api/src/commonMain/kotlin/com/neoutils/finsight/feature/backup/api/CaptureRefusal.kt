package com.neoutils.finsight.feature.backup.api

import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A destructive action, the copy owed before it, and the one question that may let it
 * happen anyway.
 *
 * [PreventiveCaptureException] refuses by being thrown, and not catching it is what aborts
 * the action — the outcome the spec asks for by default. This is where a sheet turns that
 * refusal into a question instead: it says why no copy could be taken, waits for the person
 * standing in front of it, and only their yes runs the action a second time with the
 * capture skipped. Nobody else may answer it.
 *
 * **It lives beside the exception it answers**, in the same module, because the question is
 * one rule and not one per screen: five confirmations across three features ask it, and
 * three implementations of it would be three chances for one of them to read a dismissal as
 * permission.
 *
 * **Whether to go on without a copy is the only thing decided here.** Which actions are
 * worth a copy, whether the vault is on and whether the copy already kept still covers the
 * archive are all behind [PreventiveBackup], and the skipping is expressed to the domain by
 * a flag it interprets — never by a screen deciding a rule does not apply (design D7).
 *
 * **The action is one coroutine, from the first attempt to the last.** The answer travels
 * back into it through [answer] rather than being parked in a field somebody has to come
 * back for, which is what makes leaving safe: the sheet going away cancels the scope, the
 * wait below is cancelled with it, and nothing was destroyed.
 */
class CaptureRefusal {

    private val _reason = MutableStateFlow<UiText?>(null)

    /**
     * Why no copy could be taken, while the question about it is up, and `null` whenever
     * there is nothing to answer.
     *
     * A modal is rendered outside the sheet's own tree, by the manager that holds it, so
     * what keeps the two in step is this flow rather than a value that was true once.
     */
    val reason: StateFlow<UiText?> = _reason.asStateFlow()

    private var answer: CompletableDeferred<Boolean>? = null

    /**
     * Runs [action] with the copy taken first and, when none could be, asks whether to run
     * it again with nothing kept back.
     *
     * The second run is the same action, and it is reached only through a yes. A no — and a
     * question walked away from, which is a no — ends here, with whatever the action was
     * about to destroy exactly as it was.
     */
    suspend fun attempt(action: suspend (withoutCopy: Boolean) -> Unit) {
        try {
            action(false)
        } catch (cause: PreventiveCaptureException) {
            if (await(cause.reason)) action(true)
        }
    }

    /**
     * The question was answered; the coroutine waiting mid-action goes on from where it is,
     * either into the action or out of it.
     *
     * The answer is taken before anything else happens, so a second tap — and the dismissal
     * that follows a first one — finds nothing left to answer with.
     */
    fun answer(proceed: Boolean) {
        val pending = answer ?: return
        answer = null
        pending.complete(proceed)
    }

    private suspend fun await(reason: UiText): Boolean {
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        _reason.value = reason

        return try {
            pending.await()
        } finally {
            answer = null
            _reason.value = null
        }
    }
}
