package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpToolResult
import com.neoutils.finsight.mcp.surface.AgentRefusal
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
 * Nothing here decides anything. Every refusal below is one the domain already stated, and every
 * resolution is a lookup — which is the line `mcp-tool-surface` draws: composing, translating and
 * resolving a name into an identity are the tool's work, and what an operation *means* is not.
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

/** A reference to what an act produced, so the user reaches the posting from the log. */
internal fun reference(kind: AgentActivity.Reference.Kind, id: Long) =
    AgentActivity.Reference(kind = kind, id = id)

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
 * The identity no category has, and therefore how a call says *none* about a field whose absence
 * already means something else.
 *
 * Wherever absence means *keep what it has* — an edit that carries every field, a confirmation
 * pre-filled from its template — leaving `category_id` out is the one thing that cannot also mean
 * "no category", and an explicit `null` is read as absence too ([argument]). Zero is what the
 * surface answers with, once, so that two tools asking the same question of the same caller cannot
 * drift into answering it differently.
 */
internal const val NO_CATEGORY = 0L

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
