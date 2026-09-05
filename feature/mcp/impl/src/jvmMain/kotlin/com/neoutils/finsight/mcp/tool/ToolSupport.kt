package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpToolResult
import com.neoutils.finsight.mcp.surface.AgentRefusal
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/**
 * The plumbing every tool shares: how a payload is written, how an argument is read, and how a
 * malformed argument is refused.
 *
 * Nothing here decides anything about money. What lives here is the translation between the wire and
 * Kotlin — the layer that has to exist somewhere and must not be written once per tool, with eight
 * slightly different answers to "what does an absent `month` mean".
 */

/**
 * How every payload is written.
 *
 * `explicitNulls = false` because an absent key and a `null` say the same thing here, and the
 * shorter one costs the consumer less of its context. `encodeDefaults = true` because the opposite
 * drops exactly the fields most often at their default and most needed — `"is_in_progress": false`
 * is the whole point of that field.
 */
internal val agentJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

/** A payload, as the agent receives it. */
internal inline fun <reified T> answer(payload: T) = McpToolResult(
    text = agentJson.encodeToString(payload),
)

/**
 * A refusal, as the agent receives it.
 *
 * The outcome is carried so the protocol marks the call as an error the agent has to read and act
 * on rather than a result to relay. Nothing is recorded: these tools only read, and the journal
 * keeps what changed.
 */
internal fun refused(refusal: AgentRefusal) = McpToolResult(
    text = agentJson.encodeToString(refusal),
    outcome = AgentActivity.Outcome.REFUSED,
    summary = refusal.reason,
    detail = refusal.reason,
)

/**
 * Runs the body of a read, turning an argument the tool cannot use into the refusal that names it.
 *
 * Without this the malformed argument would reach the journal's catch-all and come back as *"the
 * operation could not be completed"*, which tells the agent nothing it can act on — and an agent
 * that cannot tell a bad argument from a missing capability retries the same call.
 */
internal suspend fun reading(block: suspend () -> McpToolResult): McpToolResult =
    try {
        block()
    } catch (bad: BadArgument) {
        refused(bad.refusal)
    }

/** The day the app is living in — from the injected clock, never `Clock.System` at a call site. */
internal fun Clock.today(): LocalDate = todayIn(TimeZone.currentSystemDefault())

// ----------------------------------------------------------------------------------
// Reading arguments
// ----------------------------------------------------------------------------------

/** An argument that arrived in a shape the tool cannot use. Refused by name, never guessed at. */
internal class BadArgument(val refusal: AgentRefusal) : Exception(refusal.reason)

private fun bad(name: String, expected: String, got: String): Nothing = throw BadArgument(
    AgentRefusal(reason = "`$name` must be $expected, but `$got` was given."),
)

/**
 * What the call put under [name], with an explicit `null` read as nothing at all.
 *
 * Every reader below is built on this, because *"the caller said nothing"* is one question and has
 * to have one answer. Most clients serialise an optional field they were given nothing for as an
 * explicit `null`, and [JsonNull] is a [JsonPrimitive] whose content is the four-character string
 * `null` — read without this, `{"month": null}` is a month called `null`, `{"name": null}` names a
 * category `null`, and `{"amount": null}` is an amount the tool refuses as malformed.
 */
internal fun JsonObject?.argument(name: String): JsonElement? =
    this?.get(name)?.takeIf { it !is JsonNull }

internal fun JsonObject?.string(name: String): String? =
    (argument(name) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject?.long(name: String): Long? {
    val raw = argument(name) ?: return null
    val primitive = raw as? JsonPrimitive ?: bad(name, "a number", raw.toString())
    return primitive.content.toLongOrNull() ?: bad(name, "a number", primitive.content)
}

/**
 * A list of identities. Absent means the caller did not narrow by them; a `null` **inside** the
 * list is an element that names nothing, and that is a malformed list rather than an absent one.
 */
internal fun JsonObject?.longs(name: String): List<Long>? {
    val raw = argument(name) ?: return null
    val array = raw as? JsonArray ?: bad(name, "an array of numbers", raw.toString())
    return array.map { element ->
        (element as? JsonPrimitive)?.content?.toLongOrNull()
            ?: bad(name, "an array of numbers", element.toString())
    }
}

/**
 * A count — a page size, an offset — clamped to the range the tool states rather than refused.
 *
 * Clamped and not refused because there is nothing here for a caller to correct: asking for a
 * thousand items and getting two hundred with `has_more` set is a complete answer, while a refusal
 * costs a round trip to learn a number the description already gives. A malformed value is still a
 * refusal — that is [long]'s doing, and it is a different mistake.
 */
internal fun JsonObject?.count(name: String, default: Int, max: Int, min: Int = 0): Int =
    long(name)?.coerceIn(min.toLong(), max.toLong())?.toInt() ?: default

/** A month as `2026-03`. Absent means the month the app is in, which is what a person means. */
internal fun JsonObject?.month(name: String, clock: Clock): YearMonth =
    monthOrNull(name) ?: clock.today().yearMonth

/** An optional month, absent meaning the caller did not ask about one at all. */
internal fun JsonObject?.monthOrNull(name: String): YearMonth? {
    val raw = string(name) ?: return null
    return runCatching { YearMonth.parse(raw) }.getOrElse { bad(name, "a month as `2026-03`", raw) }
}

/** A date as `2026-03-14`. */
internal fun JsonObject?.date(name: String): LocalDate? {
    val raw = string(name) ?: return null
    return runCatching { LocalDate.parse(raw) }
        .getOrElse { bad(name, "a date as `2026-03-14`", raw) }
}

/** A yes-or-no argument. Absent means [default], which every caller of this states. */
internal fun JsonObject?.flag(name: String, default: Boolean): Boolean {
    val raw = argument(name) ?: return default
    val primitive = raw as? JsonPrimitive ?: bad(name, "`true` or `false`", raw.toString())
    return primitive.content.toBooleanStrictOrNull()
        ?: bad(name, "`true` or `false`", primitive.content)
}

/** One of a closed set of words, refused by name when it is none of them. */
internal fun JsonObject?.oneOf(name: String, allowed: List<String>): String? {
    val raw = string(name)?.lowercase() ?: return null
    if (raw !in allowed) bad(name, "one of ${allowed.joinToString(", ")}", raw)
    return raw
}

// ----------------------------------------------------------------------------------
// Declaring what a tool takes
// ----------------------------------------------------------------------------------

internal fun schema(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
) = ToolSchema(
    properties = JsonObject(properties.toMap()),
    required = required.ifEmpty { null },
)

internal fun text(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

/**
 * A parameter that accepts exactly the words listed.
 *
 * The list is the schema's and the description's at once, so a discriminated tool cannot describe in
 * prose a value its parameter refuses. That divergence is invisible until a call fails, and it
 * teaches the consumer to distrust the only material it has for choosing.
 */
internal fun choice(description: String, values: List<String>): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", "$description One of: ${values.joinToString(", ")}.")
    putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
}

internal fun yesOrNo(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

internal fun number(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

internal fun numbers(description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "integer") }
}
