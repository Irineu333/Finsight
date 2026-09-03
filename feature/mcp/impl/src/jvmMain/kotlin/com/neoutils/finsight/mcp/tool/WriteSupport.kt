package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpToolResult
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentTransaction
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/**
 * The plumbing every tool that **changes** something shares: how an act is reported, how a refusal
 * the domain raised reaches the agent, and how an identity an argument names is resolved.
 *
 * Nothing here decides what an operation *means*. Every refusal below is one the domain already
 * stated, and every resolution is a lookup — which is the line `mcp-tool-surface` draws: composing,
 * translating and resolving a name into an identity are the tool's work. The one convention it owns
 * is [NO_CATEGORY], which is a fact about the wire and not about the ledger.
 */

// ----------------------------------------------------------------------------------
// How an act is reported
// ----------------------------------------------------------------------------------

/**
 * An act that went through.
 *
 * [summary] is what the activity log keeps, in the user's own words **as they were true at that
 * instant**, and [reference] is what lets them reach it afterwards. Both are required of a write
 * rather than defaulted: an entry that says only `create_transaction` tells the person reading the
 * log nothing they came for, and one with no reference is a sentence with nothing behind it.
 */
internal inline fun <reified T> applied(
    payload: T,
    summary: String,
    reference: AgentActivity.Reference,
) = McpToolResult(
    text = agentJson.encodeToString(payload),
    outcome = AgentActivity.Outcome.APPLIED,
    summary = summary,
    reference = reference,
)

/**
 * An act the domain refused, in the domain's own words.
 *
 * [summary] still describes what was attempted, because the refusal is exactly what the user came to
 * the log to understand — "why did the agent say it could not delete that category" is answered by
 * the attempt and the reason side by side, and neither alone.
 */
internal fun refusedBy(cause: Throwable, summary: String): McpToolResult =
    AgentRefusal.fromDomain(cause).let {
        McpToolResult(
            text = agentJson.encodeToString(it),
            outcome = AgentActivity.Outcome.REFUSED,
            summary = summary,
            detail = it.reason,
        )
    }

/** A refusal the tool itself has to make, before the domain is reached: an identity that matches nothing. */
internal fun refusedWith(refusal: AgentRefusal, summary: String): McpToolResult = McpToolResult(
    text = agentJson.encodeToString(refusal),
    outcome = AgentActivity.Outcome.REFUSED,
    summary = summary,
    detail = refusal.reason,
)

/**
 * The tail every write shares: the use case answered, and either outcome has to reach both the agent
 * and the log.
 */
internal inline fun <T, reified P> Either<Throwable, T>.reported(
    summary: String,
    payload: (T) -> P,
    reference: (T) -> AgentActivity.Reference,
): McpToolResult = fold(
    ifLeft = { refusedBy(it, summary) },
    ifRight = { applied(payload(it), summary, reference(it)) },
)

/**
 * Runs the body of a write, turning an argument the tool cannot use into the refusal that names it.
 *
 * Named apart from [reading] and doing the same thing on purpose: what differs is not the plumbing
 * but the record. A read that is handed a malformed argument leaves nothing behind, and a write that
 * is — even one that never reached the domain — is an attempt the log has to hold, which is why
 * [BadArgument] arrives here as a [McpToolResult] carrying its outcome rather than as an exception
 * the journal would report as a defect.
 */
internal suspend fun writing(block: suspend () -> McpToolResult): McpToolResult =
    try {
        block()
    } catch (bad: BadArgument) {
        refusedWith(bad.refusal, summary = bad.refusal.reason)
    }

/**
 * Runs the removal a tool was asked for, turning a copy the vault could not take into the refusal
 * that says so.
 *
 * [PreventiveCaptureException] is the one refusal of the domain that arrives as a throw, and it is
 * thrown for a reason: the copy is owed *before* the rows go, so a caller free to leave the refusal
 * unread would be the one destroying something with nothing behind it. A screen answers it by asking
 * the person — that is `CaptureRefusal`, and only their yes runs the removal again with the capture
 * skipped. **This surface has nobody to ask, and does not ask on their behalf**: it reaches no tool
 * that touches the vault, because capturing and configuring backups is declared out of scope
 * ([McpSurface][com.neoutils.finsight.mcp.McpSurface]), and a flag that skipped the copy per call
 * would hand an agent the very safeguard the user switched on, one removal at a time.
 *
 * **So the refusal is where the agent's part ends, and naming it is the whole of this.** Left to the
 * journal's catch-all the removal comes back as *"the operation could not be completed"* — a
 * sentence with nothing in it about a copy, a vault or a backup. Told that and nothing else, an
 * agent looks for the fault in the posting it was asked to remove and reports the data as broken.
 * Told what actually stopped it, it has one thing to relay and the person has one thing to fix.
 */
internal suspend fun removing(
    summary: String,
    remove: suspend () -> McpToolResult,
): McpToolResult = try {
    remove()
} catch (refused: PreventiveCaptureException) {
    // The vault's own words, for the reason `AgentRefusal.fromDomain` uses the domain's: a second
    // wording maintained here would be a second answer to "why not". The trailing stop is this
    // sentence's, because the messages it quotes carry none.
    val why = refused.message?.takeIf { it.isNotBlank() }?.trimEnd('.')

    refusedWith(
        AgentRefusal(
            reason = "Nothing was removed. The preventive backup vault is on, so a copy of the " +
                "archive is owed before a deletion, and this one could not be taken" +
                (why?.let { ": $it" } ?: "") + ". Repairing the destination or switching the " +
                "vault off is done in the app's backup settings, which no tool of this surface " +
                "reaches — a person has to answer this one before the removal can be asked for " +
                "again.",
        ),
        summary = summary,
    )
}

/** A reference to what an act produced, so the user reaches the posting from the log. */
internal fun reference(kind: AgentActivity.Reference.Kind, id: Long) =
    AgentActivity.Reference(kind = kind, id = id)

/**
 * What a posting write says it did — and, when the posting it wrote has no leg to be read through,
 * that the answer names none.
 *
 * `Transaction.toAgentTransaction` keeps `null` for a transaction it cannot present, so a listing
 * drops the item instead of failing on it. A write has nothing to drop: the ledger already holds
 * the posting by the time the mapping runs, and asserting the `null` away would raise on the far
 * side of it — where [AgentActivityJournal][com.neoutils.finsight.mcp.AgentActivityJournal] turns
 * any throw into `"The operation could not be completed."` under `REFUSED`. An applied write
 * reported as a refusal is the one answer an agent is invited to repeat, and repeating it writes
 * the posting a second time.
 */
internal fun noteFor(posting: AgentTransaction?, done: String): String = when (posting) {
    null -> "$done The posting is in the ledger; this answer does not name it, because it has no " +
        "leg to be read through."

    else -> done
}

// ----------------------------------------------------------------------------------
// Reading the arguments a write takes
// ----------------------------------------------------------------------------------

/** An identity the tool cannot proceed without. Absent is refused, never guessed at. */
internal fun JsonObject?.requiredLong(name: String): Long =
    long(name) ?: throw BadArgument(AgentRefusal(reason = "`$name` is required."))

/** A word the tool cannot proceed without, from a closed set. */
internal fun JsonObject?.requiredOneOf(name: String, allowed: List<String>): String =
    oneOf(name, allowed) ?: throw BadArgument(
        AgentRefusal(reason = "`$name` is required, and must be one of ${allowed.joinToString(", ")}."),
    )

/** A sentence the tool cannot proceed without. */
internal fun JsonObject?.requiredString(name: String): String =
    string(name) ?: throw BadArgument(AgentRefusal(reason = "`$name` is required."))

/**
 * Whether the call **said** anything about a field — which is a different question from what it
 * said.
 *
 * It exists for the one case where absence and emptiness are different intentions: a tool that
 * pre-fills from a template has to tell *"say nothing, give me the template's"* from *"this cycle
 * genuinely has none"*, and both arrive as something [string] and [long] answer `null` to. A key
 * carrying an explicit `null` said nothing — that is [argument]'s decision, not a second one here.
 */
internal fun JsonObject?.names(name: String): Boolean = argument(name) != null

/**
 * A sentence a write carries over from what it edits, where **empty is an erasure and absent is
 * not**.
 *
 * [names] asked at the one place the answer decides what gets written. A title is something a user
 * takes back, so a call carrying `""` has said *this has none* while a call carrying nothing has
 * said nothing at all — and `string(name) ?: carried` cannot tell them apart, because [string]
 * answers `null` to both. Under that reading the erasure becomes a no-op the answer still reports
 * as applied, which is the one outcome a write must never produce.
 *
 * What a name may then be taken away *to* is not settled here: an operation with neither a title
 * nor a category is refused by the domain that owns that rule, reached like any other write.
 */
internal fun JsonObject?.stringOr(name: String, carried: String?): String? =
    if (names(name)) string(name) else carried

/**
 * The identity no category has, and therefore how a call says *none* where absence already means
 * something else.
 *
 * `update_transaction`, `update_recurring` and `confirm_recurring` all carry what the call does not
 * name — from the posting, from the template it edits, from the template it is a cycle of — so for
 * them leaving `category_id` out cannot also mean "no category", and an explicit `null` reads as
 * absence too ([argument]). Zero is what the three read it as, from here rather than from a
 * constant each. A creation has no such need: absence there already means none, and `0` stays an
 * identity matching nothing.
 */
internal const val NO_CATEGORY = 0L

/**
 * The other half of [NO_CATEGORY], said where the caller reads it: the creations take no `0`.
 *
 * A `const` is not a place an agent ever looks, and the two halves of the surface answer `0`
 * differently — the three carrying tools read it as *none*, the creations resolve it as an identity
 * and refuse it as a category that does not exist. An agent that learns the convention on an edit
 * carries it to a creation and gets an answer about the category rather than about the tool, from
 * which the condition cannot be deduced. So the condition travels with the argument it governs.
 */
internal const val NO_CATEGORY_ON_CREATION =
    "Leave it out for no category: a creation carries nothing over, so absence already says " +
        "unclassified and `0` is an identity that matches nothing."

/**
 * A yes-or-no the caller may leave out entirely.
 *
 * Distinct from [flag] with a default, and the distinction is what makes an edit safe: on a write
 * that carries every field, absent has to mean *keep what it has* rather than *false*, or an agent
 * changing a name would clear a flag it never mentioned. Absent is [argument]'s sense of it, so a
 * field carrying an explicit `null` was left out.
 */
internal fun JsonObject?.flagOrNull(name: String): Boolean? =
    if (argument(name) == null) null else flag(name, default = false)

/**
 * An amount, as the agent states it: a number in the currency's own unit — `45.9`, never `4590`.
 *
 * It is symmetric with what every figure of this surface answers in, which is the point: an agent
 * that reads `45.9` off a listing and writes `45.9` back cannot be off by a factor of a hundred.
 * The digits-in-cents string the form takes is an internal shape of the form, and turning one into
 * the other is translation.
 */
internal fun JsonObject?.money(name: String): Double? {
    val raw = argument(name) ?: return null
    val text = raw.toString().trim('"')
    return text.toDoubleOrNull()
        ?: throw BadArgument(AgentRefusal(reason = "`$name` must be an amount, but `$text` was given."))
}

internal fun JsonObject?.requiredMoney(name: String): Double =
    money(name) ?: throw BadArgument(AgentRefusal(reason = "`$name` is required."))

/** The digits-in-cents the transaction and recurring forms are written in. */
internal fun Double.asFormAmount(): String = (this * 100).roundToLong().toString()

/** The `dd/MM/yyyy` the transaction form is written in. */
internal fun LocalDate.asFormDate(): String = dayMonthYear.format(this)

/** A parameter that takes an amount in the currency's own unit, decimals and all. */
internal fun amount(description: String): JsonObject = buildJsonObject {
    put("type", "number")
    put("description", description)
}

// ----------------------------------------------------------------------------------
// Resolving what an identity names
// ----------------------------------------------------------------------------------

/**
 * The facade an identity names, or the refusal that says which identity did not resolve.
 *
 * A tool resolves before it acts because the use cases take identities and refuse the ones that
 * match nothing — with a message about *an* account, not about the one the agent asked for. Saying
 * which is what lets an agent tell "I resolved the wrong name" from "the domain said no", and the
 * two call for opposite next moves.
 */
internal suspend fun IAccountRepository.require(accountId: Long): Account =
    getAccountById(accountId) ?: throw BadArgument(AgentRefusal.notFound("account", accountId))

internal suspend fun ICreditCardRepository.require(cardId: Long): CreditCard =
    getCreditCardById(cardId) ?: throw BadArgument(AgentRefusal.notFound("card", cardId))

internal suspend fun ICategoryRepository.require(categoryId: Long): Category =
    getCategoryById(categoryId) ?: throw BadArgument(AgentRefusal.notFound("category", categoryId))
