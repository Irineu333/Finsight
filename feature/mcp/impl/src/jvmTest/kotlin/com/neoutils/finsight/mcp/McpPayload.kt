package com.neoutils.finsight.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotNull

/**
 * Reading what the server actually put on the wire.
 *
 * The tests that use this call the tools **through the protocol**, over a real socket, because that
 * is the only place the whole path exists: the schema the SDK validates the arguments against, the
 * serialisation of the payload, and the error flag a refusal has to arrive with. A tool exercised by
 * calling its Kotlin function proves the composition and none of that.
 */
private val json = Json { ignoreUnknownKeys = true }

/**
 * The JSON-RPC envelope, whichever framing the transport chose: a plain JSON body, or the last
 * `data:` line of an event stream.
 */
internal fun RawHttp.Response.jsonRpc(): JsonObject {
    val payload = body.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .lastOrNull()
        ?: body.trim()

    return runCatching { json.parseToJsonElement(payload).jsonObject }
        .getOrElse { error("Not a JSON-RPC response (status $status):\n$body") }
}

/** The text a tool answered with — the payload, before it is parsed. */
internal fun RawHttp.Response.toolText(): String {
    val envelope = jsonRpc()
    val result = assertNotNull(
        envelope["result"]?.jsonObject,
        "The call did not produce a result: $envelope",
    )
    return result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
}

/** The payload a tool answered with, parsed. */
internal fun RawHttp.Response.payload(): JsonObject = json.parseToJsonElement(toolText()).jsonObject

/** Whether the tool refused — which the protocol carries as an error the agent has to read. */
internal fun RawHttp.Response.isToolError(): Boolean =
    jsonRpc()["result"]?.jsonObject?.get("isError")?.jsonPrimitive?.boolean == true

/** The tools the server announced, by name. */
internal fun RawHttp.Response.announcedToolNames(): List<String> =
    jsonRpc()["result"]!!.jsonObject["tools"]!!.jsonArray
        .map { it.jsonObject["name"]!!.jsonPrimitive.content }

/** The description the server announced for one tool — the only material an agent has to choose by. */
internal fun RawHttp.Response.announcedDescription(name: String): String =
    jsonRpc()["result"]!!.jsonObject["tools"]!!.jsonArray
        .single { it.jsonObject["name"]!!.jsonPrimitive.content == name }
        .jsonObject["description"]!!.jsonPrimitive.content

// ----------------------------------------------------------------------------------
// Reading a payload
// ----------------------------------------------------------------------------------

/** The object at a path, failing with what it was looking in rather than with a null. */
internal fun JsonObject.at(vararg path: String): JsonObject {
    var current = this
    path.forEach { step ->
        current = assertNotNull(current[step], "no `$step` in $current").jsonObject
    }
    return current
}

/** The single number a figure reduced to, or `null` when it has none. */
internal fun JsonObject.amount(): Double? = this["amount"]?.jsonPrimitive?.content?.toDouble()

internal fun JsonObject.currency(): String? = this["currency"]?.jsonPrimitive?.content

internal fun JsonObject.text(field: String): String? = this[field]?.jsonPrimitive?.content

internal fun JsonObject.number(field: String): Double? = this[field]?.jsonPrimitive?.content?.toDouble()

internal fun JsonObject.flag(field: String): Boolean? = this[field]?.jsonPrimitive?.boolean

/** A figure's decomposition, as `currency to amount`. */
internal fun JsonObject.byCurrency(): Map<String, Double> =
    this["by_currency"]?.jsonArray.orEmpty().associate {
        it.jsonObject["currency"]!!.jsonPrimitive.content to
            it.jsonObject["amount"]!!.jsonPrimitive.content.toDouble()
    }
