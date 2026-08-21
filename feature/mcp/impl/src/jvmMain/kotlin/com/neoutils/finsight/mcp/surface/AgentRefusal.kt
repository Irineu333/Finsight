package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.model.retireActionOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a tool answers with when the domain says no.
 *
 * **A refusal that only says no teaches the agent to invent a way round it.** The one this surface
 * was designed against is real: told it could not delete a posting, an agent considered editing its
 * amount to zero to "neutralise" it — which would have left a zero-value row in every listing and
 * every count, gone from the totals and still in the history. So a refusal carries the reason, and
 * where the domain allows something else in place of what was asked, it carries the name of that
 * too.
 *
 * [reason] is the domain's own words. Every error type in the app already states its reason in
 * English for logging, and that string is what belongs here — a second wording maintained beside it
 * would be a second answer to "why not", one edit away from disagreeing with the first.
 */
@Serializable
internal data class AgentRefusal(
    /** Why the domain refused, in the words the domain itself puts it in. */
    val reason: String,
    /**
     * The tool that does what the domain allows in place of what was asked, or `null` when there is
     * nothing to offer. A name the agent can call, never a suggestion in prose.
     */
    @SerialName("try_instead")
    val tryInstead: String? = null,
) {
    companion object {

        /**
         * An identity that matches nothing. It names **what** was not found rather than reporting a
         * generic failure: an agent that resolved a name to the wrong id has to be able to tell
         * that from an operation the domain refused on its merits.
         */
        fun notFound(kind: String, id: Long) = AgentRefusal(
            reason = "No $kind with id $id exists.",
        )

        /**
         * A removal the domain refuses because what it names has to be preserved — it carries
         * postings, or something else still points at it.
         *
         * Which operation stands in its place is not decided here: [retireActionOf] is the single
         * owner of archive-versus-delete, and it is the same one the screens ask, so the agent is
         * offered exactly what the user is offered.
         */
        fun cannotRemove(reason: String) = AgentRefusal(
            reason = reason,
            tryInstead = when (retireActionOf(mustPreserve = true)) {
                RetireAction.ARCHIVE -> McpToolName.ARCHIVE_ENTITY.wireName
                // Unreachable while archiving is what preservation means; written out rather than
                // assumed, so a change to that rule surfaces here instead of being silently wrong.
                RetireAction.DELETE -> null
            },
        )

        /**
         * A refusal the domain raised, translated once.
         *
         * **The reason is never rewritten here.** Every error type of the app already states why it
         * refused, in English, for logging — and that sentence is the one the agent gets. A second
         * wording maintained beside it would be a second answer to "why not", free to drift from the
         * first with nothing failing.
         *
         * What this decides is only the *alternative*, and only for the refusals that mean **what
         * was named has to be preserved**: those are the ones with somewhere else to go, and
         * [cannotRemove] asks `retireActionOf` — the same owner the screens ask — which operation
         * that is. Everything else is a plain no: an amount that is not positive has no other tool
         * to offer, and pointing at one would be worse than saying nothing.
         */
        fun fromDomain(cause: Throwable): AgentRefusal {
            val reason = cause.message?.takeIf { it.isNotBlank() }
                ?: cause::class.simpleName
                ?: "The operation was refused."

            return when {
                cause is RetireException -> cannotRemove(reason)
                cause is RecurringRetireException -> cannotRemove(reason)
                cause is AccountException && cause.error in PRESERVES -> cannotRemove(reason)
                else -> AgentRefusal(reason)
            }
        }

        /**
         * The two account refusals that mean preservation rather than a bad request — they are
         * raised by the removal of a card as well, which is why they are read off the error and
         * not off the tool that hit them.
         */
        private val PRESERVES = setOf(AccountError.HAS_TRANSACTIONS, AccountError.HAS_RECURRING)
    }
}
