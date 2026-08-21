package com.neoutils.finsight.feature.mcp.api

import kotlin.time.Instant

/**
 * One act an agent performed: when it happened, which operation it was, what it was about in
 * words the user recognises, how it ended, and what it reached.
 *
 * This is the app's record of **authorship**. Everything else shows the result — the posting
 * is simply there — and a write that came from outside is otherwise indistinguishable from one
 * the user forgot making. It is also the only thing standing between the user and the silent
 * duplication that a repeated call produces: the two acts appear side by side, with their
 * times, instead of waiting to be noticed in the middle of a statement.
 *
 * **It is a trace and not accounting truth.** What was posted is in the ledger, where every
 * figure comes from. Discarding one of these changes no figure, and nothing derives a number
 * from them.
 *
 * **A query never becomes one of these.** An agent asks dozens of questions to answer one, and
 * recording them would bury the handful of acts that actually changed something; a read alters
 * nothing and has nothing to audit.
 */
data class AgentActivity(
    val id: Long = 0,
    /** When the act happened. */
    val at: Instant,
    /**
     * The stable identity of the operation — the tool's own name, as the agent called it.
     *
     * It is an identity, so it survives a rewording; turning it into something to read is the
     * section's job.
     */
    val operation: String,
    /**
     * What the act was about, in the user's words, **as they were true when it happened**.
     *
     * It names the account, the category or the card the way they were named at that instant,
     * and it is never rewritten afterwards: this is testimony about a past act, and refreshing
     * it when something is renamed would falsify the record. It stays a description rather than
     * a second source of truth because nothing ever reads it back as data — what the act
     * produced is reached through [reference], not through this sentence.
     */
    val summary: String,
    val outcome: Outcome,
    /** Why the act was refused. `null` when it was applied — there is nothing to explain. */
    val detail: String? = null,
    /** What the act created or changed, or `null` when it created and changed nothing. */
    val reference: Reference? = null,
) {

    /**
     * How the act ended: it went through, or something said no.
     *
     * A refusal is recorded because it is what explains to the user why the agent reported that
     * it could not do something.
     */
    enum class Outcome {
        /** The operation went through, and the ledger holds its result. */
        APPLIED,

        /** Permission or the domain refused it; nothing was written, and [detail] says why. */
        REFUSED,
    }

    /**
     * What an act created or changed, as an identity — the half of the record that stays live.
     *
     * It is what lets the user reach the posting from the log and see it as it is now, rather
     * than as the frozen sentence in [summary] describes it. It is deliberately not a foreign
     * key: the log must never keep a posting from being deleted, so a reference may well name
     * something that no longer exists — which is itself a fact worth showing.
     */
    data class Reference(
        val kind: Kind,
        val id: Long,
    ) {
        /** The kind of thing a reference names, which is what says where to resolve it. */
        enum class Kind {
            TRANSACTION,
            ACCOUNT,
            CATEGORY,
            CREDIT_CARD,
            INVOICE,
            INSTALLMENT,
            RECURRING,
            BUDGET,
        }
    }
}
