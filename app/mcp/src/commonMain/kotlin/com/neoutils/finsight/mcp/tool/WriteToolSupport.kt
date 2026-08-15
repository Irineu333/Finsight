package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.Idempotency
import com.neoutils.finsight.mcp.write.IdempotencyCodes
import com.neoutils.finsight.mcp.write.IdempotencyStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The argument every write tool carries a call's idempotency key in. */
internal const val IDEMPOTENCY_KEY: String = "idempotencyKey"

/** The argument every write tool carries its items in — one to many, always an array. */
internal const val ITEMS: String = "items"

/**
 * The largest batch a single call may carry.
 *
 * The same ceiling a listing serves, and for the same reason: a call answers one item per
 * item it was given, so an unbounded batch is an unbounded response. It is **refused, naming
 * the ceiling**, before anything is written — a batch truncated halfway would leave the
 * caller believing thirty lines were recorded when fifteen were.
 */
internal const val MAX_ITEMS_PER_CALL: Int = ResponseLimits.MAX_PAGE_SIZE

/** The refusals a batch call earns before any item is looked at. */
internal object BatchCodes {

    /** `items` is absent, empty, or not an array of objects. */
    const val ITEMS_REQUIRED: String = "ITEMS_REQUIRED"

    /** The batch carries more items than [MAX_ITEMS_PER_CALL]. */
    const val TOO_MANY_ITEMS: String = "TOO_MANY_ITEMS"

    /** An item of the batch is not an object. */
    const val ITEM_NOT_AN_OBJECT: String = "ITEM_NOT_AN_OBJECT"

    /** The identifier of a record this call was to act on named nothing. */
    const val TRANSACTION_NOT_FOUND: String = "TRANSACTION_NOT_FOUND"

    val all: Set<String> = setOf(ITEMS_REQUIRED, TOO_MANY_ITEMS, ITEM_NOT_AN_OBJECT, TRANSACTION_NOT_FOUND) +
        IdempotencyCodes.all
}

/**
 * How one item of a batch ended — the three outcomes the surface distinguishes.
 *
 * They are reported **per item**: an aggregate success over thirty lines does not let a
 * consumer correct the one that failed without reprocessing all thirty.
 */
enum class WriteItemStatus {

    /** The item was written. Its identifiers and the label the domain derived come with it. */
    APPLIED,

    /** The item was refused. **Nothing was written for it**, and the refusal says why. */
    REFUSED,

    /**
     * The item had already been written by an earlier call carrying the same idempotency
     * key, and was **not written again**.
     *
     * This is what makes an interrupted batch resumable: what was applied stays applied,
     * and repeating the call with the same key finishes what is missing instead of
     * doubling what is not.
     */
    SKIPPED_AS_DUPLICATE,
}

/**
 * The outcome of one item of a batch, as the response states it.
 *
 * [warnings] belong to the item and not to the call: a probable duplicate is a fact about
 * one line of a statement, and a consumer that received it at the level of the call could
 * not tell which line it was about.
 */
internal data class WriteItemOutcome(
    val index: Int,
    val status: WriteItemStatus,
    /** Every transaction the item wrote — several, when it is an installment plan. */
    val transactionIds: List<Long> = emptyList(),
    /** The labels the domain derived. No item ever declares one. */
    val labels: List<String> = emptyList(),
    val error: ToolError? = null,
    val warnings: List<ToolWarning> = emptyList(),
) {
    companion object {

        fun applied(
            index: Int,
            transactionIds: List<Long>,
            labels: List<String>,
            warnings: List<ToolWarning> = emptyList(),
        ) = WriteItemOutcome(
            index = index,
            status = WriteItemStatus.APPLIED,
            transactionIds = transactionIds,
            labels = labels,
            warnings = warnings,
        )

        fun refused(index: Int, error: ToolError, warnings: List<ToolWarning> = emptyList()) = WriteItemOutcome(
            index = index,
            status = WriteItemStatus.REFUSED,
            error = error,
            warnings = warnings,
        )
    }
}

/** One item outcome, as this surface serialises one. */
internal fun WriteItemOutcome.asJson(): JsonObject = buildJsonObject {
    put("index", index)
    put("status", status.name)
    if (transactionIds.isNotEmpty()) {
        putJsonArray("transactionIds") { transactionIds.forEach { add(it) } }
    }
    if (labels.isNotEmpty()) {
        putJsonArray("labels") { labels.forEach { add(it) } }
    }
    error?.let { put("error", ToolJson.encodeToJsonElement(it)) }
    if (warnings.isNotEmpty()) {
        put("warnings", ToolJson.encodeToJsonElement(warnings))
    }
}

/**
 * The whole batch's answer: how many items were applied, and **what became of each one**.
 *
 * The counts never travel without [items]: an aggregate success with no per-item detail is
 * exactly what this shape exists to make impossible.
 */
internal fun batchResult(outcomes: List<WriteItemOutcome>): JsonObject = buildJsonObject {
    put("appliedCount", outcomes.count { it.status == WriteItemStatus.APPLIED })
    put("refusedCount", outcomes.count { it.status == WriteItemStatus.REFUSED })
    put("skippedAsDuplicateCount", outcomes.count { it.status == WriteItemStatus.SKIPPED_AS_DUPLICATE })
    putJsonArray("items") { outcomes.forEach { add(it.asJson()) } }
}

/** The items of a batch call, or the refusal that keeps the call from starting. */
internal fun readItems(arguments: JsonObject): Either<ToolError, List<JsonObject>> {
    val array = arguments[ITEMS] as? JsonArray ?: return Either.Left(
        ToolError.invalidInput(
            code = BatchCodes.ITEMS_REQUIRED,
            message = "`$ITEMS` is required, and is an array of one or more items. " +
                "Recording a statement is one call with its lines in it, not one call per line.",
        ),
    )

    if (array.isEmpty()) {
        return Either.Left(
            ToolError.invalidInput(
                code = BatchCodes.ITEMS_REQUIRED,
                message = "`$ITEMS` is empty; a call carries at least one item.",
            ),
        )
    }

    if (array.size > MAX_ITEMS_PER_CALL) {
        return Either.Left(
            ToolError.invalidInput(
                code = BatchCodes.TOO_MANY_ITEMS,
                message = "This call carries ${array.size} items and the maximum is $MAX_ITEMS_PER_CALL. " +
                    "Nothing was written; split the batch and repeat, with a new idempotency key per call.",
            ),
        )
    }

    return Either.Right(
        array.mapIndexed { index, element ->
            element as? JsonObject ?: return Either.Left(
                ToolError.invalidInput(
                    code = BatchCodes.ITEM_NOT_AN_OBJECT,
                    message = "Item $index is not an object. Nothing was written.",
                ),
            )
        },
    )
}

/**
 * Runs one batch of a write tool: the idempotency key, the items, the journal.
 *
 * **The envelope, and no rule of its own.** What an item *means* is [perItem]'s, which ends
 * in the use case that owns it; this decides only what a repeat means and what the journal
 * records.
 *
 * The key is evaluated twice over, and the two readings answer different questions:
 *
 * - **For the call**, together with a fingerprint of all of its arguments. The very same
 *   call is answered with the response the first one produced, and the same key carrying
 *   *different* arguments is a conflict — nothing is written, because turning a second
 *   consignment into a no-op would discard legitimate transactions in silence.
 * - **For each item**, under `key#index`, remembered as soon as that item is written. That
 *   is what gives an interrupted batch a defined outcome: whatever was applied stays
 *   applied, and repeating the call with the same key writes only what is still missing,
 *   reporting the rest as [WriteItemStatus.SKIPPED_AS_DUPLICATE].
 *
 * A refused item is deliberately **not** remembered: it wrote nothing, so there is nothing
 * a repeat would double, and a failure that was merely transient deserves the second try.
 *
 * The answer is a successful outcome carrying every item's own outcome — refusals included.
 * A refused *item* does not make the call an error: the call was understood and executed,
 * and a failed outcome carries no result, so marking it would discard the very per-item
 * detail the surface exists to report. What the call itself refuses — unreadable arguments,
 * a reused key, the permission level — is a failed outcome, with nothing written.
 */
internal suspend fun McpTool.runBatch(
    arguments: JsonObject,
    idempotency: IdempotencyStore,
    activity: ActivityRecorder,
    perItem: suspend (index: Int, item: JsonObject) -> WriteItemOutcome,
): ToolOutcome {
    val key = (arguments[IDEMPOTENCY_KEY] as? JsonPrimitive)?.content

    val items = when (val read = readItems(arguments)) {
        is Either.Left -> return refuse(read.value, arguments, activity)
        is Either.Right -> read.value
    }

    when (val decision = idempotency.evaluate(key, arguments)) {
        is Idempotency.Conflict -> return refuse(decision.error, arguments, activity)
        // Answered with what the first call produced, verbatim: that is what the record
        // kept it for, and re-executing would be the duplication the key exists to prevent.
        is Idempotency.Replay -> {
            activity.record(this, arguments, decision.outcome)
            return decision.outcome
        }

        Idempotency.Proceed -> Unit
    }

    val outcomes = mutableListOf<WriteItemOutcome>()
    items.forEachIndexed { index, item ->
        val itemKey = key?.let { "$it#$index" }
        val outcome = when (val decision = idempotency.evaluate(itemKey, item)) {
            is Idempotency.Replay -> decision.outcome.asSkipped(index)
            is Idempotency.Conflict -> WriteItemOutcome.refused(index, decision.error)
            Idempotency.Proceed -> perItem(index, item)
        }

        if (outcome.status == WriteItemStatus.APPLIED) {
            idempotency.remember(itemKey, item, ToolOutcome.Ok(outcome.asJson()))
        }
        outcomes += outcome
    }

    val result = ToolOutcome.Ok(
        result = batchResult(outcomes),
        warnings = outcomes.flatMap { it.warnings },
    )

    idempotency.remember(key, arguments, result)
    activity.record(
        tool = this,
        arguments = arguments,
        outcome = result,
        affected = outcomes.flatMap { it.transactionIds }.map { "transaction:$it" },
    )

    return result
}

/** A refusal of the call itself: nothing was written, and the journal says so. */
private suspend fun McpTool.refuse(
    error: ToolError,
    arguments: JsonObject,
    activity: ActivityRecorder,
): ToolOutcome = ToolOutcome.Failed(error).also { activity.record(this, arguments, it) }

/**
 * The item as an earlier call under the same key left it, restated as skipped.
 *
 * The identifiers are kept: what the consumer needs to know is that these transactions
 * exist and that this call did not write them a second time.
 */
private fun ToolOutcome.asSkipped(index: Int): WriteItemOutcome {
    val remembered = (this as? ToolOutcome.Ok)?.result
    val ids = (remembered?.get("transactionIds") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
        .orEmpty()
    val labels = (remembered?.get("labels") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        .orEmpty()

    return WriteItemOutcome(
        index = index,
        status = WriteItemStatus.SKIPPED_AS_DUPLICATE,
        transactionIds = ids,
        labels = labels,
    )
}

// ---------------------------------------------------------------------------
// JSON Schema, shared by the write surface
// ---------------------------------------------------------------------------

/** The key argument, declared identically by every write tool. */
internal fun JsonObjectBuilder.idempotencyKeyProperty() = stringProperty(
    name = IDEMPOTENCY_KEY,
    description = "Optional. Repeating a call with the same key and the same arguments writes " +
        "nothing again and answers what the first call produced; the same key with different " +
        "arguments is refused as a conflict, and nothing is written.",
)

/** The items argument, declared identically by every batch tool. */
internal fun JsonObjectBuilder.itemsProperty(item: JsonObject, description: String) = arrayProperty(
    name = ITEMS,
    items = item,
    description = "$description One to $MAX_ITEMS_PER_CALL items per call.",
)

/** The shape of one item's outcome — the same one for every write tool. */
internal val writeItemOutcomeSchema: JsonObject = objectSchema(required = listOf("index", "status")) {
    integerProperty("index", "The position of this item in the `items` array of the call.")
    enumProperty(
        name = "status",
        values = WriteItemStatus.entries.map { it.name },
        description = "APPLIED, REFUSED — nothing was written for it — or SKIPPED_AS_DUPLICATE, " +
            "already written by an earlier call carrying the same idempotency key.",
    )
    arrayProperty(
        name = "transactionIds",
        items = buildJsonObject { put("type", "integer") },
        description = "Every transaction this item wrote. Several for an installment plan, " +
            "which is one operation and not one call per payment.",
    )
    arrayProperty(
        name = "labels",
        items = buildJsonObject { put("type", "string") },
        description = "The labels the domain derived for what was written. No item declares one.",
    )
    objectProperty(
        name = "error",
        schema = objectSchema(required = listOf("category", "code", "message", "isRetryable")) {
            stringProperty("category", "The class of the refusal — branch on this, never on the message.")
            stringProperty("code", "Stable, enumerated by this tool's output schema.")
            stringProperty("message", "English, for a log. Never text destined for a screen.")
            booleanProperty("isRetryable", "Whether the very same item may be attempted again.")
        },
    )
    arrayProperty(
        name = "warnings",
        items = objectSchema(required = listOf("code", "message")) {
            stringProperty("code", "PROBABLE_DUPLICATE, for one.")
            stringProperty("message", "English, for a log.")
            objectProperty("details", objectSchema { })
        },
        description = "Present on an item that was written all the same — a warning never blocks a write.",
    )
}

/** The shape of a batch's answer: the counts, and every item's own outcome. */
internal val batchResultSchema: JsonObject = objectSchema(
    required = listOf("appliedCount", "refusedCount", "skippedAsDuplicateCount", "items"),
) {
    integerProperty("appliedCount", "How many items were written by this call.")
    integerProperty("refusedCount", "How many were refused. Nothing was written for those.")
    integerProperty("skippedAsDuplicateCount", "How many an earlier call under the same key had already written.")
    arrayProperty(
        name = "items",
        items = writeItemOutcomeSchema,
        description = "One outcome per item, in the order the items were given. Always present: " +
            "an aggregate success without per-item detail is never returned.",
    )
}
