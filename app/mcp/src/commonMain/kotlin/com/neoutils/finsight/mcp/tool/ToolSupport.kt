package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.CivilDate
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.Page
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.parseCivilDate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The serialiser every tool encodes its payload with.
 *
 * The same settings the contract's own envelope uses, and for the same reasons:
 * defaults are written out (a consumer that infers a schema from examples must see every
 * field), nulls are omitted (an absent field is absent, not `null`), and a sealed
 * hierarchy is discriminated by `kind`.
 */
internal val ToolJson = Json {
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}

/** The codes every tool can emit, whatever else it declares. */
internal object CommonToolCodes {

    /** An argument is present but not of the type the schema declares. */
    const val INVALID_ARGUMENT: String = "INVALID_ARGUMENT"

    /** An argument names a value outside the enumeration the schema declares. */
    const val UNKNOWN_ENUM_VALUE: String = "UNKNOWN_ENUM_VALUE"

    /** An identifier named nothing. */
    const val NOT_FOUND: String = "NOT_FOUND"

    /** The set every tool adds to its own codes. */
    val all: Set<String> = setOf(INVALID_ARGUMENT, UNKNOWN_ENUM_VALUE, NOT_FOUND)
}

/**
 * A reader over a call's arguments that **accumulates the first refusal instead of
 * throwing**.
 *
 * A tool refuses with a [ToolOutcome.Failed] and never with an exception, so reading an
 * argument cannot throw either. Every accessor answers `null` once something has already
 * been refused, which lets a tool read all of its arguments in a straight line and check
 * [failure] once at the end.
 */
internal class Arguments(private val json: JsonObject) {

    /** The first refusal any accessor produced, or `null` while the call is still valid. */
    var failure: ToolError? = null
        private set

    fun refuse(error: ToolError): Nothing? {
        if (failure == null) failure = error
        return null
    }

    private fun primitive(key: String): JsonPrimitive? = when (val element = json[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> element
        else -> refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.INVALID_ARGUMENT,
                message = "`$key` must be a scalar value",
            ),
        )
    }

    fun string(key: String): String? = if (failure != null) null else primitive(key)?.content

    fun long(key: String): Long? {
        val raw = if (failure != null) null else primitive(key) ?: return null
        return raw?.content?.toLongOrNull() ?: refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.INVALID_ARGUMENT,
                message = "`$key` must be an integer identifier; received `${raw?.content}`",
            ),
        )
    }

    fun int(key: String): Int? = long(key)?.let { value ->
        value.toInt().takeIf { it.toLong() == value } ?: refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.INVALID_ARGUMENT,
                message = "`$key` is out of range: $value",
            ),
        )
    }

    fun double(key: String): Double? {
        val raw = if (failure != null) null else primitive(key) ?: return null
        return raw?.content?.toDoubleOrNull() ?: refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.INVALID_ARGUMENT,
                message = "`$key` must be a number; received `${raw?.content}`",
            ),
        )
    }

    fun boolean(key: String): Boolean? {
        val raw = if (failure != null) null else primitive(key) ?: return null
        return when (raw?.content) {
            null -> null
            "true" -> true
            "false" -> false
            else -> refuse(
                ToolError.invalidInput(
                    code = CommonToolCodes.INVALID_ARGUMENT,
                    message = "`$key` must be true or false; received `${raw.content}`",
                ),
            )
        }
    }

    /**
     * A civil date, `YYYY-MM-DD`. Natural language is refused by [parseCivilDate], which
     * owns that rule for the whole surface.
     */
    fun date(key: String): LocalDate? {
        val raw = string(key) ?: return null
        return when (val parsed = parseCivilDate(raw)) {
            is CivilDate.Accepted -> parsed.date
            is CivilDate.Refused -> refuse(parsed.error)
        }
    }

    /** One value of [values], matched by name. */
    fun <T : Enum<T>> enum(key: String, values: Array<T>): T? {
        val raw = string(key) ?: return null
        return values.firstOrNull { it.name == raw } ?: refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.UNKNOWN_ENUM_VALUE,
                message = "`$key` must be one of ${values.joinToString { it.name }}; received `$raw`",
            ),
        )
    }

    fun longs(key: String): List<Long>? {
        val element = json[key]
        if (element == null || element == JsonNull) return null
        val array = element as? JsonArray ?: return refuse(
            ToolError.invalidInput(
                code = CommonToolCodes.INVALID_ARGUMENT,
                message = "`$key` must be an array of integer identifiers",
            ),
        )
        return array.map { item ->
            (item as? JsonPrimitive)?.content?.toLongOrNull() ?: return refuse(
                ToolError.invalidInput(
                    code = CommonToolCodes.INVALID_ARGUMENT,
                    message = "every element of `$key` must be an integer identifier",
                ),
            )
        }
    }
}

/**
 * Cuts [items] into one page, resuming **after** the record [cursor] stands for.
 *
 * The cursor carries a key and not a position, so a record written between two pages
 * neither duplicates nor hides one: the resumption point is a record, and it either
 * exists — in which case the page starts after it — or it does not, in which case the
 * listing starts over rather than silently skipping into the middle.
 *
 * @param key the stable key of a record, which the cursor of the page before encoded.
 */
internal fun <T> paginate(
    items: List<T>,
    limit: Int,
    cursor: Cursor?,
    key: (T) -> String,
): Page<T> {
    val resumeAfter = cursor?.decode()
    val start = when (resumeAfter) {
        null -> 0
        else -> items.indexOfFirst { key(it) == resumeAfter }.let { if (it < 0) 0 else it + 1 }
    }
    val window = items.drop(start).take(limit)
    val next = window.lastOrNull()
        ?.takeIf { start + window.size < items.size }
        ?.let { Cursor.of(key(it)) }

    return Page(items = window, totalMatching = items.size, nextCursor = next)
}

/**
 * The same page, carrying the encoded form of its items.
 *
 * A page is cut over domain records and serialised afterwards, because the encoding of a
 * record may itself need to read the ledger — and paginating after that would read the
 * whole listing to answer for fifty of it.
 */
internal fun Page<*>.with(items: List<JsonObject>): Page<JsonObject> =
    Page(items = items, totalMatching = totalMatching, nextCursor = nextCursor)

/** The page, as this surface serialises one — its items already encoded. */
internal fun JsonObjectBuilder.putPage(name: String, page: Page<JsonObject>) {
    putJsonArray(name) { page.items.forEach { add(it) } }
    put("totalMatching", page.totalMatching)
    page.nextCursor?.let { put("nextCursor", it.value) }
}

/** The echo of everything the server assumed, on every read. */
internal fun JsonObjectBuilder.putAssumed(assumed: AssumedDefaults) {
    put("assumed", ToolJson.encodeToJsonElement(assumed))
}

/** A successful outcome built from a payload and, optionally, its warnings. */
internal fun ok(
    warnings: List<ToolWarning> = emptyList(),
    build: JsonObjectBuilder.() -> Unit,
): ToolOutcome = ToolOutcome.Ok(result = buildJsonObject(build), warnings = warnings)

/** How many bytes the structured content of [outcome] occupies on the wire. */
internal fun sizeOf(outcome: ToolOutcome): Int =
    outcome.structuredContent.toString().encodeToByteArray().size

// ---------------------------------------------------------------------------
// JSON Schema, written by hand because it is the tool's public contract
// ---------------------------------------------------------------------------

internal fun objectSchema(
    required: List<String> = emptyList(),
    properties: JsonObjectBuilder.() -> Unit,
): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties", properties)
    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
}

internal fun JsonObjectBuilder.stringProperty(name: String, description: String) =
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
    }

internal fun JsonObjectBuilder.integerProperty(name: String, description: String) =
    putJsonObject(name) {
        put("type", "integer")
        put("description", description)
    }

internal fun JsonObjectBuilder.numberProperty(name: String, description: String) =
    putJsonObject(name) {
        put("type", "number")
        put("description", description)
    }

internal fun JsonObjectBuilder.booleanProperty(name: String, description: String) =
    putJsonObject(name) {
        put("type", "boolean")
        put("description", description)
    }

internal fun JsonObjectBuilder.enumProperty(name: String, values: List<String>, description: String) =
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
        putJsonArray("enum") { values.forEach { add(it) } }
    }

internal fun JsonObjectBuilder.arrayProperty(name: String, items: JsonObject, description: String) =
    putJsonObject(name) {
        put("type", "array")
        put("description", description)
        put("items", items)
    }

internal fun JsonObjectBuilder.objectProperty(name: String, schema: JsonObject) =
    put(name, schema)

/**
 * The shape of one denominated amount, as `MoneyAmount` serialises.
 *
 * Declared once because every payload of this surface carries money, and a schema
 * repeated nine times would eventually be nine schemas.
 */
internal val moneyAmountSchema: JsonObject = objectSchema(required = listOf("currency", "minorUnits", "scale")) {
    stringProperty("currency", "ISO 4217 code.")
    integerProperty("minorUnits", "The amount in the minor unit — cents —, signed as displayed.")
    integerProperty("scale", "The exponent of ten relating the minor unit to the major one.")
    stringProperty("formattedForDisplayOnly", "A caption. Never parse it back — `minorUnits` is the value.")
}

/**
 * The shape of a figure a read that can span accounts answers with.
 *
 * `amounts` is a collection **even with a single currency in it**, and the tools whose
 * answers carry one say so in their own descriptions too.
 */
internal val moneyByCurrencySchema: JsonObject = objectSchema(required = listOf("amounts")) {
    arrayProperty(
        name = "amounts",
        items = moneyAmountSchema,
        description = "One amount per currency. A collection always — never a scalar, " +
            "not even when the user holds a single currency.",
    )
    objectProperty(
        name = "consolidated",
        schema = objectSchema {
            stringProperty("kind", "`available` or `unavailable`.")
            objectProperty("amount", moneyAmountSchema)
            stringProperty("asOf", "The date whose rates produced the number.")
            stringProperty("reason", "Why there is no consolidated number.")
            stringProperty("message", "English, for a log.")
            booleanProperty("isStale", "Whether any applied rate predates the date it was applied to.")
            arrayProperty(
                name = "appliedRates",
                items = objectSchema {
                    stringProperty("currency", "The currency being priced.")
                    stringProperty("counterCurrency", "The currency it is priced in.")
                    numberProperty("rate", "Units of the counter currency per one unit.")
                    stringProperty("date", "The day this rate is an observation about.")
                    booleanProperty("isStale", "Whether the observation predates the date applied to.")
                },
                description = "Every rate applied, so the number is reproducible.",
            )
        },
    )
}

/** The shape of the defaults echoed on every read. */
internal val assumedSchema: JsonObject = objectSchema(required = listOf("timeZone", "referenceDate", "archived")) {
    stringProperty("timeZone", "The IANA zone every date in this response is civil in.")
    objectProperty(
        name = "referenceDate",
        schema = objectSchema { stringProperty("value", "The date."); booleanProperty("wasAssumed", "Whether the server chose it.") },
    )
    objectProperty(
        name = "period",
        schema = objectSchema {
            objectProperty(
                name = "value",
                schema = objectSchema {
                    stringProperty("start", "Inclusive.")
                    stringProperty("end", "Inclusive.")
                },
            )
            booleanProperty("wasAssumed", "Whether the server chose it.")
        },
    )
    objectProperty(
        name = "archived",
        schema = objectSchema {
            enumProperty("value", listOf("EXCLUDED", "INCLUDED", "ONLY"), "Which side of the archived line was read.")
            booleanProperty("wasAssumed", "Whether the server chose it.")
        },
    )
}

/** The two properties every paginated listing takes. */
internal fun JsonObjectBuilder.pagingProperties() {
    integerProperty("limit", "Page size. Above the declared ceiling the call is refused, never truncated.")
    stringProperty("cursor", "Opaque. It comes from `nextCursor`; it is never an offset to do arithmetic on.")
}

/** The two properties every paginated listing answers with, beside its items. */
internal fun JsonObjectBuilder.pagingResultProperties() {
    integerProperty("totalMatching", "How many records satisfy the filter — not how many are on this page.")
    stringProperty("nextCursor", "Where the next page resumes; absent when this page ends the listing.")
}

/** The archived-scope argument, shared by every listing of a facade that can be archived. */
internal fun JsonObjectBuilder.archivedProperty() = enumProperty(
    name = "archived",
    values = listOf("EXCLUDED", "INCLUDED", "ONLY"),
    description = "Archived records are EXCLUDED by default, and the scope applied is echoed in `assumed`.",
)

/** The identifier and the name of a nested object, so no consumer resolves a name twice. */
internal fun JsonObjectBuilder.putRef(name: String, id: Long, label: String) = putJsonObject(name) {
    put("id", id)
    put("name", label)
}

/** The schema of the pair above. */
internal fun refSchema(description: String): JsonObject = objectSchema(required = listOf("id", "name")) {
    integerProperty("id", "The opaque identifier. Names are never keys on this surface.")
    stringProperty("name", description)
}

/** The array of a listing's items, in the shape a listing declares them. */
internal fun listingSchema(itemsName: String, item: JsonObject, description: String): JsonObject =
    objectSchema(required = listOf(itemsName, "totalMatching", "assumed")) {
        arrayProperty(itemsName, item, description)
        pagingResultProperties()
        objectProperty("assumed", assumedSchema)
    }

