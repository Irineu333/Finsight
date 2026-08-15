package com.neoutils.finsight.mcp.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The serialiser of everything this contract puts into structured content. */
internal val ContractJson = Json {
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}

/**
 * What kind of refusal this is — the first thing a consumer branches on, and the reason
 * it never has to read the message to decide.
 *
 * [isRetryable] is a property of the class and not of the instance: a refusal by a rule of
 * the domain is **never** retryable, because the state of the system is correct and trying
 * again produces the same refusal; an unavailability and an internal failure always are,
 * because they say nothing about whether the operation was allowed.
 */
enum class ToolErrorCategory(val isRetryable: Boolean) {

    /**
     * A rule of the domain refused it — a closed invoice, an archived account, a transfer
     * to the same account. **Nothing was written**, and the agent has learned the rule
     * rather than a reason to try again.
     */
    DOMAIN_RULE(isRetryable = false),

    /** An identifier named nothing. */
    NOT_FOUND(isRetryable = false),

    /** The arguments do not describe an operation this tool can attempt. */
    INVALID_INPUT(isRetryable = false),

    /** The call collides with something already done — a reused idempotency key, notably. */
    CONFLICT(isRetryable = false),

    /** The server could not attend to it now — a rate limit, a resource in use. */
    UNAVAILABLE(isRetryable = true),

    /** It broke. The state of the system says nothing about whether it was allowed. */
    INTERNAL(isRetryable = true),
}

/**
 * A refusal, as the agent receives it.
 *
 * **[message] is English and destined for a log.** The internationalised text of the
 * project's error types — `toUiText()` — MUST NOT cross this boundary: the agent is a
 * consumer of logs, not of screens, and a tool answering Portuguese to an English client
 * would be leaking the presentation layer through a boundary that is not one.
 *
 * [code] is stable and enumerated in the tool's output schema, so a consumer can branch on
 * it without parsing prose, and so that rewording a message never breaks anyone.
 */
@Serializable
data class ToolError(
    val category: ToolErrorCategory,
    /** `UPPER_SNAKE_CASE`, stable across releases, enumerated by the tool's output schema. */
    val code: String,
    /** English, for a log. Never the translated text of a screen. */
    val message: String,
    /**
     * Whether the very same call may be attempted again. Derived from [category] and
     * verified against it, so a tool cannot promise a retry the class forbids.
     */
    val isRetryable: Boolean,
) {
    init {
        require(code.isNotBlank() && code.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' }) {
            "A tool error code is UPPER_SNAKE_CASE and never blank: `$code`"
        }
        require(message.isNotBlank()) { "A tool error carries an English message for a log" }
        require(isRetryable == category.isRetryable) {
            "$category is retryable=${category.isRetryable}; the error declares $isRetryable"
        }
    }

    companion object {
        /** A rule of the domain refused it. Never retryable. */
        fun domainRule(code: String, message: String) = of(ToolErrorCategory.DOMAIN_RULE, code, message)

        fun notFound(code: String, message: String) = of(ToolErrorCategory.NOT_FOUND, code, message)

        fun invalidInput(code: String, message: String) = of(ToolErrorCategory.INVALID_INPUT, code, message)

        fun conflict(code: String, message: String) = of(ToolErrorCategory.CONFLICT, code, message)

        fun unavailable(code: String, message: String) = of(ToolErrorCategory.UNAVAILABLE, code, message)

        fun internal(code: String, message: String) = of(ToolErrorCategory.INTERNAL, code, message)

        private fun of(category: ToolErrorCategory, code: String, message: String) =
            ToolError(category, code, message, category.isRetryable)
    }
}

/** What a warning is about — enumerated, because a warning is a field and not prose. */
enum class ToolWarningCode {

    /**
     * A figure could not be reduced to the base currency for want of a rate. **Not an
     * error**: the per-currency figure is complete, and the call succeeded.
     */
    MISSING_EXCHANGE_RATE,

    /** A rate was applied that is an observation older than the date it was applied to. */
    STALE_EXCHANGE_RATE,

    /** An item matches an existing transaction closely enough to be worth a second look. */
    PROBABLE_DUPLICATE,
}

/**
 * Something the consumer should know about a result that **succeeded**.
 *
 * Structured, and never prose: outside the structured content declared by the output
 * schema a warning is text, and text is dropped by the first host that renders only what
 * it has a schema for.
 */
@Serializable
data class ToolWarning(
    val code: ToolWarningCode,
    /** English, for a log — the same rule the errors follow. */
    val message: String,
    /**
     * What the warning is about, as fields: the currency without a rate, the index of the
     * item that looks duplicated. Values are strings because the consumer branches on
     * [code], not on these.
     */
    val details: Map<String, String> = emptyMap(),
)

/**
 * How a tool call ended.
 *
 * **A refusal is an error of the tool's execution, marked as such inside a result the
 * transport reports as a success.** The protocol keeps two channels: a malformed request,
 * an unknown method or an unsupported version is a JSON-RPC error; an operation the server
 * understood and executed and then refused is a tool execution error. Returning a refusal
 * as an ordinary result with an error object inside it looks identical to a success to
 * every host that reads only the marking — and the consumer then tells the user the
 * operation was done.
 *
 * [structuredContent] is the whole answer, refusals and warnings included: it is what the
 * tool's `outputSchema` describes, and it is the only part of a response that survives.
 */
sealed interface ToolOutcome {

    /** Whether the transport marks this result as a tool execution error. */
    val isError: Boolean

    /** Warnings, on a successful result as much as on a refused one. */
    val warnings: List<ToolWarning>

    /** The answer, under the shape [toolOutcomeSchema] declares. */
    val structuredContent: JsonObject

    /** The tool did what it was asked. It may still have something to warn about. */
    data class Ok(
        val result: JsonObject,
        override val warnings: List<ToolWarning> = emptyList(),
    ) : ToolOutcome {

        override val isError: Boolean get() = false

        override val structuredContent: JsonObject
            get() = buildJsonObject {
                put("isError", false)
                put("result", result)
                put("warnings", ContractJson.encodeToJsonElement(warnings))
            }
    }

    /**
     * The tool refused, or broke. Nothing about the shape of this changes with the
     * category — that is what lets a consumer read the outcome before it reads the tool.
     */
    data class Failed(
        val error: ToolError,
        override val warnings: List<ToolWarning> = emptyList(),
    ) : ToolOutcome {

        override val isError: Boolean get() = true

        override val structuredContent: JsonObject
            get() = buildJsonObject {
                put("isError", true)
                put("error", ContractJson.encodeToJsonElement(error))
                put("warnings", ContractJson.encodeToJsonElement(warnings))
            }
    }
}

/**
 * The output schema of any tool: the envelope this contract fixes, wrapped around the
 * result shape the tool itself declares.
 *
 * It exists so that "where is the error, where are the warnings" is answered once for the
 * whole surface rather than once per tool, and so that the codes a tool can emit are
 * **enumerated in the schema** — which is what lets a consumer branch on them without
 * having seen one first.
 *
 * @param resultSchema the JSON Schema of the tool's own payload, present only on success.
 * @param errorCodes every code this tool can emit. Empty is refused: a tool that can be
 * called can be refused, if only for invalid input.
 */
fun toolOutcomeSchema(resultSchema: JsonObject, errorCodes: Set<String>): JsonObject {
    require(errorCodes.isNotEmpty()) { "A tool enumerates the error codes it can emit" }

    return buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("isError") {
                put("type", "boolean")
                put("description", "True when this result is a tool execution error.")
            }
            put("result", resultSchema)
            putJsonObject("error") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("category") {
                        put("type", "string")
                        putJsonArray("enum") {
                            ToolErrorCategory.entries.forEach { add(it.name) }
                        }
                    }
                    putJsonObject("code") {
                        put("type", "string")
                        putJsonArray("enum") { errorCodes.sorted().forEach { add(it) } }
                    }
                    putJsonObject("message") {
                        put("type", "string")
                        put("description", "English, for a log. Never text destined for a screen.")
                    }
                    putJsonObject("isRetryable") { put("type", "boolean") }
                }
                putJsonArray("required") {
                    add("category"); add("code"); add("message"); add("isRetryable")
                }
            }
            putJsonObject("warnings") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("code") {
                            put("type", "string")
                            putJsonArray("enum") { ToolWarningCode.entries.forEach { add(it.name) } }
                        }
                        putJsonObject("message") { put("type", "string") }
                        putJsonObject("details") { put("type", "object") }
                    }
                    putJsonArray("required") { add("code"); add("message") }
                }
            }
        }
        putJsonArray("required") { add("isError"); add("warnings") }
    }
}

/** The warning a read raises when it succeeded but could not reduce a figure to one number. */
fun ConsolidatedMoney.Unavailable.asWarning() = ToolWarning(
    code = ToolWarningCode.MISSING_EXCHANGE_RATE,
    message = message,
    details = mapOf("reason" to reason.name),
)
