package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.MONEY_SCALE
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.ToolWarningCode
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.DomainRefusalCodes
import com.neoutils.finsight.mcp.write.IdempotencyStore
import com.neoutils.finsight.mcp.write.TransactionItemResolver
import com.neoutils.finsight.mcp.write.WriteIntent
import com.neoutils.finsight.mcp.write.WriteItemCodes
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToLong

/** The name of the recording tool, named by the dry run and by the prompts. */
const val RECORD_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}record_transactions"

/**
 * Records one to many transactions in a single call.
 *
 * **A batch is composition, never a rule.** Every item goes through
 * [TransactionItemResolver], which ends in the use case that owns that intent, so an item
 * of a batch produces exactly what the same item alone would produce. Nothing here decides
 * which invoice a purchase falls in, whether a category classifies a direction, or how a
 * transfer across currencies balances.
 *
 * The answer states **what became of each item** and how many were applied. A partial
 * failure is therefore visible and correctable without reprocessing the batch, which is the
 * whole reason a statement is one call instead of thirty.
 */
class RecordTransactionsTool(
    private val resolver: TransactionItemResolver,
    private val duplicates: ProbableDuplicates,
    private val idempotency: IdempotencyStore,
    private val activity: ActivityRecorder,
) : McpTool {

    override val name: String = RECORD_TRANSACTIONS_TOOL

    override val title: String = "Record transactions"

    override val description: String = """
        Records one to many transactions in a single call — a whole statement is one call
        with its lines in `items`, not one call per line.

        Each item states an **intent** and the identities involved. No item carries ledger
        legs, signed amounts or the transaction's label: what a transaction is comes from
        the accounts it touches, and it comes back derived in `labels`.

        Money is stated as `{ currency, minorUnits }` — an integer in the minor unit, never
        a decimal number. Identifiers come from a previous read; a name is refused, never
        matched, and no category is ever created to satisfy an item.

        The answer carries `items`, **one outcome per item**: APPLIED with its identifiers,
        REFUSED with its error and nothing written, or SKIPPED_AS_DUPLICATE when an earlier
        call under the same `idempotencyKey` had already written it. A refused item does not
        undo the ones that were written.

        An item that looks like a transaction already on file is recorded **with a warning**
        and never blocked. To see what a call would do before making it, call
        $PREVIEW_TRANSACTIONS_TOOL, which writes nothing.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema(required = listOf(ITEMS)) {
        itemsProperty(writeItemSchema, "The transactions to record.")
        idempotencyKeyProperty()
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = batchResultSchema,
        errorCodes = CommonToolCodes.all + BatchCodes.all + WriteItemCodes.all + DomainRefusalCodes.all +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    /**
     * It writes, and repeating it under the same key writes nothing further — which is a
     * fact about this tool and not a hope: the key is honoured by the server, per call and
     * per item. It removes and overwrites nothing: recording only ever adds.
     */
    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutcome =
        runBatch(arguments, idempotency, activity) { index, item ->
            resolver.resolve(item).fold(
                ifLeft = { WriteItemOutcome.refused(index, it) },
                ifRight = { resolved ->
                    // Read before the write and reported on the item: a probable duplicate
                    // is a warning about a line, and deciding on its own that a legitimate
                    // transaction is a repeat is the graver of the two errors.
                    val warnings = listOfNotNull(duplicates.warningFor(index, resolved.preview))

                    resolved.apply().fold(
                        ifLeft = { WriteItemOutcome.refused(index, it) },
                        ifRight = { written ->
                            WriteItemOutcome.applied(
                                index = index,
                                transactionIds = written.map { it.id },
                                labels = written.map { it.label.name },
                                warnings = warnings,
                            )
                        },
                    )
                },
            )
        }
}

/**
 * Whether an item looks like a transaction already on file — **an observation, never a
 * decision**.
 *
 * The four fields the spec names have to agree: the same day, the same amount, the same
 * account, and the same description. It produces a warning and nothing else: importing the
 * same statement twice is the commonest mistake there is, and refusing a legitimate
 * transaction because it resembles an older one is the opposite mistake, made silently.
 *
 * It reads the **resolved** item — the preview the resolver produced — so the dry run and
 * the write warn about exactly the same lines, from the same data.
 */
class ProbableDuplicates(
    private val transactions: ITransactionRepository,
    private val creditCards: ICreditCardRepository,
) {

    suspend fun warningFor(index: Int, preview: JsonObject): ToolWarning? {
        val date = (preview["date"] as? JsonPrimitive)?.content
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val amount = preview["amount"] as? JsonObject ?: return null
        val minorUnits = amount["minorUnits"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val scale = amount["scale"]?.jsonPrimitive?.content?.toIntOrNull()
        if (scale != null && scale != MONEY_SCALE) return null
        val accountId = accountOf(preview) ?: return null
        val description = (preview["description"] as? JsonPrimitive)?.content.orEmpty().trim()

        val existing = transactions
            .getTransactionsBy(startDate = date, endDate = date, accountId = accountId)
            .firstOrNull { transaction ->
                (transaction.amount * 100).roundToLong() == abs(minorUnits) &&
                    transaction.title.orEmpty().trim().equals(description, ignoreCase = true)
            } ?: return null

        return ToolWarning(
            code = ToolWarningCode.PROBABLE_DUPLICATE,
            message = "Item $index matches transaction ${existing.id} on $date in date, amount, account and " +
                "description. It was recorded all the same — confirm with the user before removing either.",
            details = mapOf(
                "index" to index.toString(),
                "transactionId" to existing.id.toString(),
                "date" to date.toString(),
            ),
        )
    }

    /**
     * The account whose movement would repeat: the one the item names, or the account a
     * card projects onto — a card *is* its `LIABILITY` account in the chart, and a card
     * statement imported twice is the very case this warning exists for.
     */
    private suspend fun accountOf(preview: JsonObject): Long? {
        (preview["account"] as? JsonObject)?.get("id")?.jsonPrimitive?.content?.toLongOrNull()?.let { return it }
        val cardId = (preview["creditCard"] as? JsonObject)?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
            ?: return null
        return creditCards.getCreditCardById(cardId)?.accountId
    }
}

/** Money as an item states it — the same shape it comes back in. */
private val itemMoneySchema: JsonObject = objectSchema(required = listOf("currency", "minorUnits")) {
    stringProperty("currency", "ISO 4217 code. It must be the currency the account it posts to declares.")
    integerProperty("minorUnits", "An integer in the minor unit — cents. Never a decimal number.")
    integerProperty("scale", "Optional, and always $MONEY_SCALE when given.")
}

/**
 * The shape of one item of a write call.
 *
 * Every field of every intent is declared in one object, because a JSON Schema union of
 * seven variants is read by no client the same way. Which fields an intent requires is
 * stated in the descriptions, and refused by name when one is missing.
 */
internal val writeItemSchema: JsonObject = objectSchema(required = listOf("intent", "date")) {
    enumProperty(
        name = "intent",
        values = WriteIntent.entries.map { it.name },
        description = "What the item is. EXPENSE and INCOME need `accountId` and `amount`; " +
            "CARD_PURCHASE needs `creditCardId` and `amount`; TRANSFER needs `accountId`, " +
            "`destinationAccountId` and `amount`; INVOICE_PAYMENT needs `invoiceId` and `accountId`; " +
            "ACCOUNT_ADJUSTMENT needs `accountId` and `targetBalance`; INVOICE_ADJUSTMENT needs " +
            "`invoiceId` and `targetBalance`.",
    )
    stringProperty("date", "The day it happened, YYYY-MM-DD. Never a natural-language period.")
    stringProperty("description", "What the user would have written. Optional.")
    objectProperty("amount", itemMoneySchema)
    objectProperty(
        name = "destinationAmount",
        schema = itemMoneySchema,
    )
    integerProperty("accountId", "The account it posts to — the source account of a transfer.")
    integerProperty("destinationAccountId", "Where a transfer arrives.")
    integerProperty("creditCardId", "The card a purchase posts to.")
    integerProperty("invoiceId", "The bill, when the item names one outright.")
    integerProperty("categoryId", "An existing category. None is ever created to satisfy an item.")
    integerProperty("installments", "How many payments a card purchase is split into. One operation, whatever the count.")
    objectProperty("targetBalance", itemMoneySchema)
}
