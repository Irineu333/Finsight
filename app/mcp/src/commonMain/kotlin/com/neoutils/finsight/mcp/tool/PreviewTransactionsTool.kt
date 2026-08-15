package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import com.neoutils.finsight.mcp.write.DomainRefusalCodes
import com.neoutils.finsight.mcp.write.TransactionItemResolver
import com.neoutils.finsight.mcp.write.WriteItemCodes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The name of the dry run, named by the recording tool's own description. */
const val PREVIEW_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}preview_transactions"

/** What would become of one item, had it been sent to the recording tool. */
enum class PreviewItemStatus {

    /** It resolves, and recording it would attempt exactly what `preview` states. */
    WOULD_BE_RECORDED,

    /** It would be refused, for the reason given. Recording it would write nothing. */
    WOULD_BE_REFUSED,
}

/**
 * The dry run: **exactly what recording these items would write, and nothing persisted**.
 *
 * A tool of its own, and not a boolean on the write. A tool that is read-only or destructive
 * depending on an argument cannot be annotated honestly, and it is by the annotations that a
 * client decides whether to ask the user for confirmation.
 *
 * It answers from the **same resolver** the recording tool calls, stopping one step short:
 * [TransactionItemResolver] resolves an item into the call of the use case that owns it and
 * a description of that call, and this tool returns the description without ever performing
 * it. That is why the invoice a purchase would fall in is the invoice it will fall in —
 * there is one answer to that question, not two.
 */
class PreviewTransactionsTool(
    private val resolver: TransactionItemResolver,
    private val duplicates: ProbableDuplicates,
) : McpTool {

    override val name: String = PREVIEW_TRANSACTIONS_TOOL

    override val title: String = "Preview transactions"

    override val description: String = """
        Answers what $RECORD_TRANSACTIONS_TOOL would write for these items — the resolved
        invoice of every card purchase, the account and category every item points at, and
        the refusal of every item that would be refused — **without writing anything**.

        The items are exactly those $RECORD_TRANSACTIONS_TOOL takes, so a call can be
        previewed and then made with the same arguments.

        Nothing here persists under any argument: this tool takes no idempotency key,
        because there is nothing to repeat.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema(required = listOf(ITEMS)) {
        itemsProperty(writeItemSchema, "The transactions to resolve without writing them.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = objectSchema(required = listOf("items", "wouldBeRecordedCount", "wouldBeRefusedCount")) {
            integerProperty("wouldBeRecordedCount", "How many items would be written.")
            integerProperty("wouldBeRefusedCount", "How many would be refused.")
            arrayProperty(
                name = "items",
                items = previewItemSchema,
                description = "One outcome per item, in the order the items were given.",
            )
        },
        errorCodes = CommonToolCodes.all + BatchCodes.all + WriteItemCodes.all + DomainRefusalCodes.all +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    /** It reads. It persists nothing, whatever it is called with — see the class note. */
    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val items = when (val read = readItems(arguments)) {
            is Either.Left -> return ToolOutcome.Failed(read.value)
            is Either.Right -> read.value
        }

        val warnings = mutableListOf<ToolWarning>()
        val resolved = items.mapIndexed { index, item ->
            resolver.resolve(item).fold(
                ifLeft = { error ->
                    PreviewItemStatus.WOULD_BE_REFUSED to buildJsonObject {
                        put("index", index)
                        put("status", PreviewItemStatus.WOULD_BE_REFUSED.name)
                        put("error", ToolJson.encodeToJsonElement(error))
                    }
                },
                ifRight = { resolvedItem ->
                    // The same detector the recording tool uses, over the same resolved
                    // item: a dry run that warned about other lines than the write would
                    // be a rehearsal of a different call.
                    val warning = duplicates.warningFor(index, resolvedItem.preview)
                    warning?.let { warnings += it }

                    PreviewItemStatus.WOULD_BE_RECORDED to buildJsonObject {
                        put("index", index)
                        put("status", PreviewItemStatus.WOULD_BE_RECORDED.name)
                        put("preview", resolvedItem.preview)
                        warning?.let { put("warnings", ToolJson.encodeToJsonElement(listOf(it))) }
                    }
                },
            )
        }

        return ok(warnings = warnings) {
            put("wouldBeRecordedCount", resolved.count { it.first == PreviewItemStatus.WOULD_BE_RECORDED })
            put("wouldBeRefusedCount", resolved.count { it.first == PreviewItemStatus.WOULD_BE_REFUSED })
            putJsonArray("items") { resolved.forEach { add(it.second) } }
        }
    }
}

/** The shape of one item of the dry run's answer. */
private val previewItemSchema: JsonObject = objectSchema(required = listOf("index", "status")) {
    integerProperty("index", "The position of this item in the `items` array of the call.")
    enumProperty(
        name = "status",
        values = PreviewItemStatus.entries.map { it.name },
        description = "Whether recording this item would write it or refuse it.",
    )
    objectProperty(
        name = "preview",
        schema = objectSchema {
            stringProperty("intent", "The intent this item states.")
            stringProperty("date", "The day it would be recorded on.")
            stringProperty("description", "What would be written as its title.")
            objectProperty("amount", moneyAmountSchema)
            objectProperty("account", refSchema("The account it would post to."))
            objectProperty("destinationAccount", refSchema("Where a transfer would arrive."))
            objectProperty("creditCard", refSchema("The card a purchase would post to."))
            objectProperty("invoice", refSchema("The bill an item names outright."))
            objectProperty("category", refSchema("The category it would be classified in."))
            stringProperty(
                name = "invoiceDueMonth",
                description = "The bill a card purchase would fall in, YYYY-MM — resolved by the card's own cycle.",
            )
            integerProperty("installments", "How many payments the purchase would be written as.")
        },
    )
    objectProperty(
        name = "error",
        schema = objectSchema(required = listOf("category", "code", "message", "isRetryable")) {
            stringProperty("category", "The class of the refusal.")
            stringProperty("code", "Stable, enumerated by this tool's output schema.")
            stringProperty("message", "English, for a log.")
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
        description = "What recording this item would warn about. A warning never blocks a write.",
    )
}
