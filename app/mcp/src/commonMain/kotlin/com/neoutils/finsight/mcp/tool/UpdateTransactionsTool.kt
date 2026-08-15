package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.DomainRefusalCodes
import com.neoutils.finsight.mcp.write.IdempotencyStore
import com.neoutils.finsight.mcp.write.ItemReader
import com.neoutils.finsight.mcp.write.WriteItemCodes
import com.neoutils.finsight.mcp.write.asToolError
import com.neoutils.finsight.mcp.write.categoryTypeRefusal
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/** The name of the alteration tool. */
const val UPDATE_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}update_transactions"

/** The refusals an alteration can produce beyond the common ones. */
internal object UpdateTransactionCodes {

    /** The item names no field to change, so there is nothing to write. */
    const val NOTHING_TO_UPDATE: String = "NOTHING_TO_UPDATE"

    /**
     * This transaction is not one a rewrite can express — see the class note of the tool.
     */
    const val NOT_REWRITABLE: String = "TRANSACTION_NOT_REWRITABLE"

    /**
     * The item names a field this tool does not alter — an amount, an account, a card.
     *
     * Refused rather than ignored: an item that stated a new amount and was answered with a
     * success would have the caller believing the money changed, which is the very
     * confusion not offering the field exists to prevent.
     */
    const val FIELD_NOT_ALTERABLE: String = "FIELD_NOT_ALTERABLE"

    /** The fields an item MUST NOT carry, because this tool does not change them. */
    val notAlterable: Set<String> = setOf(
        "amount",
        "accountId",
        "destinationAccountId",
        "destinationAmount",
        "creditCardId",
        "invoiceId",
        "installments",
        "targetBalance",
    )

    val all: Set<String> = setOf(NOTHING_TO_UPDATE, NOT_REWRITABLE, FIELD_NOT_ALTERABLE)
}

/**
 * Alters existing transactions — **their category, their description and their date, and
 * nothing else**.
 *
 * Amount and account are not offered. Changing the money of a transaction is removing it and
 * creating another, and an edit that disguised that would hide the correction from whoever
 * reads the history afterwards. `UpdateTransactionUseCase` takes a whole [TransactionForm]
 * and *can* express those changes, because the app's own edit screen offers them; deciding
 * *whether* to offer an operation is a consumer's call, and this is where this surface's
 * decision lives. Every field this tool does not offer is carried over from the transaction
 * as the ledger holds it.
 *
 * **Only a transaction with a single monetary leg is offered.** That is the documented
 * precondition of `UpdateTransactionUseCase`: a rewrite deletes every old entry and rebuilds
 * from the one the form describes, so a transfer or a card payment — two monetary legs —
 * would lose its second leg without anything refusing. The domain does not guard it, so the
 * consumer honours it: an item naming such a transaction is refused, and nothing is written.
 */
class UpdateTransactionsTool(
    private val transactions: ITransactionRepository,
    private val categories: ICategoryRepository,
    private val creditCards: ICreditCardRepository,
    private val invoices: IInvoiceRepository,
    private val updateTransaction: UpdateTransactionUseCase,
    private val idempotency: IdempotencyStore,
    private val activity: ActivityRecorder,
) : McpTool {

    override val name: String = UPDATE_TRANSACTIONS_TOOL

    override val title: String = "Update transactions"

    override val description: String = """
        Alters one to many existing transactions. **Only three fields can be changed:**
        `categoryId`, `description` and `date`.

        Amount and account are not accepted, by design: changing the money of a transaction
        is removing it and creating another. Use $DELETE_TRANSACTIONS_TOOL and
        $RECORD_TRANSACTIONS_TOOL for that, so the correction is visible as one. An item
        naming `amount`, `accountId` or any other money field is **refused**, never ignored.

        A field left out of an item is left as it is. `categoryId: null` — explicitly —
        removes the classification, which is the absence of a category and never a bucket.

        Only transactions with a single monetary leg can be altered: an expense or a card
        purchase, not a transfer, a card payment or a balance adjustment. Those are refused
        item by item, and the rest of the batch is written.

        The answer carries `items`, one outcome per item.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema(required = listOf(ITEMS)) {
        itemsProperty(
            item = objectSchema(required = listOf("transactionId")) {
                integerProperty("transactionId", "The transaction to alter, as a read of this server returned it.")
                integerProperty(
                    name = "categoryId",
                    description = "An existing category, or explicitly null to leave the transaction " +
                        "unclassified. Absent means unchanged.",
                )
                stringProperty("description", "The new title. Absent means unchanged; blank clears it.")
                stringProperty("date", "The new date, YYYY-MM-DD. Absent means unchanged.")
            },
            description = "The alterations to make.",
        )
        idempotencyKeyProperty()
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = batchResultSchema,
        errorCodes = CommonToolCodes.all + BatchCodes.all + UpdateTransactionCodes.all +
            WriteItemCodes.all + DomainRefusalCodes.all + AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    /**
     * It writes, and it **overwrites**: the description, the date or the category it
     * replaces is not kept anywhere. Applying the same item twice leaves the same state,
     * which is what idempotent means here.
     */
    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutcome =
        runBatch(arguments, idempotency, activity) { index, item -> alter(index, item) }

    private suspend fun alter(index: Int, item: JsonObject): WriteItemOutcome {
        val reader = ItemReader(item)
        val transactionId = reader.id("transactionId")
        val date = if (item.containsKey("date")) reader.date("date") else null
        val description = if (item.containsKey("description")) reader.text("description") else null
        val categoryId = if (item["categoryId"] != null && item["categoryId"] != JsonNull) {
            reader.id("categoryId")
        } else {
            null
        }
        reader.failure?.let { return WriteItemOutcome.refused(index, it) }
        transactionId ?: return WriteItemOutcome.refused(
            index,
            ToolError.invalidInput(WriteItemCodes.MISSING_FIELD, "`transactionId` is required"),
        )

        UpdateTransactionCodes.notAlterable.firstOrNull { item.containsKey(it) }?.let { field ->
            return WriteItemOutcome.refused(
                index,
                ToolError.invalidInput(
                    code = UpdateTransactionCodes.FIELD_NOT_ALTERABLE,
                    message = "Item $index names `$field`, which this tool does not alter. Changing the " +
                        "money of a transaction is removing it and creating another: use " +
                        "$DELETE_TRANSACTIONS_TOOL and $RECORD_TRANSACTIONS_TOOL, so the correction shows.",
                ),
            )
        }

        val changesCategory = item.containsKey("categoryId")
        if (!changesCategory && !item.containsKey("description") && !item.containsKey("date")) {
            return WriteItemOutcome.refused(
                index,
                ToolError.invalidInput(
                    code = UpdateTransactionCodes.NOTHING_TO_UPDATE,
                    message = "Item $index names no field to change. This tool alters `categoryId`, " +
                        "`description` and `date`; amount and account are not offered.",
                ),
            )
        }

        val transaction = transactions.getTransactionById(transactionId) ?: return WriteItemOutcome.refused(
            index,
            ToolError.notFound(
                code = BatchCodes.TRANSACTION_NOT_FOUND,
                message = "No transaction with id $transactionId. Nothing was written for this item.",
            ),
        )

        transaction.rewriteRefusal()?.let { return WriteItemOutcome.refused(index, it) }

        val type = deriveTransactionType(transaction.primaryEntry?.amount ?: 0L, transaction.entries)
        val category = when {
            !changesCategory -> transaction.nominalDimensionId?.let { categories.getCategoryByDimensionId(it) }
            categoryId == null -> null
            else -> categories.getCategoryById(categoryId) ?: return WriteItemOutcome.refused(
                index,
                ToolError.invalidInput(
                    code = WriteItemCodes.CATEGORY_NOT_FOUND,
                    message = "No category with id $categoryId. Identifiers come from a read of this " +
                        "server, and nothing is created implicitly to satisfy one.",
                ),
            )
        }
        category?.let { chosen ->
            categoryTypeRefusal(chosen, type)?.let { return WriteItemOutcome.refused(index, it) }
        }

        val card = transaction.liabilityAccountId?.let { accountId ->
            creditCards.getAllCreditCardsIncludingClosed().firstOrNull { it.accountId == accountId }
        }
        val dueMonth = transaction.liabilityDimensionId?.let { dimensionId ->
            invoices.getAllInvoices().firstOrNull { it.dimensionId == dimensionId }?.dueMonth
        }

        val form = TransactionForm.from(
            type = type,
            // Carried over from the ledger, in the minor unit it is held in: the money of a
            // transaction is not this tool's to change, and re-deriving it from a formatted
            // number would put a locale between the ledger and itself.
            amount = abs(transaction.primaryEntry?.amount ?: 0L).toString(),
            title = if (item.containsKey("description")) description else transaction.title,
            date = dayMonthYear.format(date ?: transaction.date),
            category = category,
            target = if (transaction.hasLiabilityLeg) TransactionTarget.CREDIT_CARD else TransactionTarget.ACCOUNT,
            creditCard = card,
            invoiceDueMonth = dueMonth,
            account = transaction.sourceAccount,
        )

        return updateTransaction(transaction.id, form).fold(
            ifLeft = { WriteItemOutcome.refused(index, it.asToolError()) },
            ifRight = {
                WriteItemOutcome.applied(
                    index = index,
                    transactionIds = listOf(transaction.id),
                    // Unchanged by construction: the label is derived from the account
                    // natures of the legs, and none of the three fields touches one.
                    labels = listOf(transaction.label.name),
                )
            },
        )
    }
}

/**
 * Why this transaction cannot be rewritten, or `null` when it can.
 *
 * The precondition of `UpdateTransactionUseCase` and of
 * `ITransactionRepository.updateTransaction`, checked here because the domain does not
 * refuse it: a rewrite takes a single leg, so a transaction with two monetary legs would
 * silently lose one. An adjustment and an installment payment are excluded for the same
 * reason the app's own edit is not offered on them — they are written as a unit whose parts
 * a rewrite cannot restate.
 */
/**
 * The refusal, when the ledger says this transaction cannot be rewritten in place.
 *
 * The **rule** is `Transaction.isRewritable`, in `:core:ledger`, and it is read rather than
 * restated — the edit screen reads the same one. What belongs here is only the wording of the
 * refusal, which is a fact about this surface and not about the ledger.
 */
private fun Transaction.rewriteRefusal(): ToolError? {
    if (isRewritable) return null

    val reason = when {
        label == TransactionLabel.ADJUSTMENT -> "it is a balance adjustment"
        monetaryEntries.size != 1 -> "it has ${monetaryEntries.size} monetary legs, and a rewrite states one"
        installmentId != null -> "it is one payment of an installment plan"
        else -> "one of its legs is on a closed account, which accepts no further entries"
    }

    return ToolError.invalidInput(
        code = UpdateTransactionCodes.NOT_REWRITABLE,
        message = "Transaction $id cannot be altered: $reason. Nothing was written for this item.",
    )
}
