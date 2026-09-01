package com.neoutils.finsight.ui.vault

import kotlinx.coroutines.CompletableDeferred

/**
 * A question put to somebody mid-operation, and the coroutine waiting on their answer.
 *
 * A restore asks twice before it replaces anything — *is this the file you meant*, and,
 * where the copy owed first could not be taken, *go on without one* — and both are asked
 * from inside the flow that holds the file. The flow cannot go on until it is answered, and
 * a sheet cannot answer a coroutine it does not have, so the answer travels back through
 * this.
 *
 * **The rule it carries is the order, and both screens used to state it for themselves.**
 * The answer is taken out before anything else happens, so a second tap — and the dismissal
 * that follows a first one — finds nothing left to answer with, and whatever the screen has
 * to show about the answer is published before the waiting flow resumes, so the operation is
 * never over for the screen one step before the person hears the result of it. Two
 * implementations of that are two chances for one of them to let a dismissal through as a
 * yes, which is exactly what
 * [com.neoutils.finsight.feature.backup.api.CaptureRefusal] says about the same question
 * asked from a deletion.
 *
 * **What it deliberately does not own is the state each screen publishes.** The two screens
 * hold different states and name the fields differently — one of them is verifying a file it
 * was handed, the other is standing on a list — and a holder that reached into either would
 * be a third place that knows what a backup screen looks like. So each passes what to
 * publish, and this decides only when.
 */
class PendingAnswer {

    private var pending: CompletableDeferred<Boolean>? = null

    /** Whether a question is up and waiting, which is a reason to refuse a second one. */
    val isWaiting: Boolean get() = pending != null

    /**
     * Publishes a question through [publish] and waits, here, for the answer the sheet sends
     * back.
     *
     * The slot is cleared however the wait ends, cancellation included: a screen that went
     * away has no question up, and the next operation may put its own.
     */
    suspend fun ask(publish: () -> Unit): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        publish()

        return try {
            deferred.await()
        } finally {
            pending = null
        }
    }

    /**
     * Answers the question that is up, if one is, letting the waiting flow go on from where
     * it stopped — and doing nothing at all when there is nothing to answer.
     *
     * [publish] runs after the answer has been taken and before the flow is released, which
     * is the order the class comment argues for.
     */
    fun answer(proceed: Boolean, publish: () -> Unit = {}) {
        val deferred = pending ?: return
        pending = null
        publish()
        deferred.complete(proceed)
    }
}
