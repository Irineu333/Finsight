package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
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
import kotlinx.serialization.json.JsonObject

/** The name of the removal tool, named by the alteration tool's description. */
const val DELETE_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}delete_transactions"

/**
 * Removes one to many transactions, each through `DeleteTransactionUseCase`.
 *
 * What decides *whether* a transaction may go is the write boundary's — a paid invoice is
 * immutable, an archived account keeps its balance — and it stays there: an item this
 * surface cannot remove comes back as the domain's own refusal, item by item, with the rest
 * of the batch removed all the same.
 */
class DeleteTransactionsTool(
    private val transactions: ITransactionRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val idempotency: IdempotencyStore,
    private val activity: ActivityRecorder,
) : McpTool {

    override val name: String = DELETE_TRANSACTIONS_TOOL

    override val title: String = "Delete transactions"

    override val description: String = """
        Removes one to many transactions. **What is removed is gone**: there is no undo on
        this surface, and the transaction's ledger entries go with it.

        Removing a transaction the domain protects — one on an archived account, one in a
        paid invoice — is refused for that item, naming the rule, and the other items of the
        batch are removed all the same.

        The answer carries `items`, one outcome per item: APPLIED with the identifier that
        was removed, or REFUSED with its error.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema(required = listOf(ITEMS)) {
        itemsProperty(
            item = objectSchema(required = listOf("transactionId")) {
                integerProperty("transactionId", "The transaction to remove, as a read of this server returned it.")
            },
            description = "The transactions to remove.",
        )
        idempotencyKeyProperty()
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = batchResultSchema,
        errorCodes = CommonToolCodes.all + BatchCodes.all + WriteItemCodes.all + DomainRefusalCodes.all,
    )

    /**
     * **Destructive**, truthfully: this is the annotation by which a client decides to ask
     * the user before calling it. Removing the same transaction again finds nothing to
     * remove, which is what idempotent means here.
     */
    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutcome =
        runBatch(arguments, idempotency, activity) { index, item -> remove(index, item) }

    private suspend fun remove(index: Int, item: JsonObject): WriteItemOutcome {
        val reader = ItemReader(item)
        val transactionId = reader.id("transactionId")
        reader.failure?.let { return WriteItemOutcome.refused(index, it) }
        transactionId ?: return WriteItemOutcome.refused(
            index,
            ToolError.invalidInput(WriteItemCodes.MISSING_FIELD, "`transactionId` is required"),
        )

        val transaction = transactions.getTransactionById(transactionId) ?: return WriteItemOutcome.refused(
            index,
            ToolError.notFound(
                code = BatchCodes.TRANSACTION_NOT_FOUND,
                message = "No transaction with id $transactionId. Nothing was removed for this item.",
            ),
        )

        return deleteTransaction(transaction).fold(
            ifLeft = { WriteItemOutcome.refused(index, it.asToolError()) },
            ifRight = {
                WriteItemOutcome.applied(
                    index = index,
                    transactionIds = listOf(transaction.id),
                    labels = listOf(transaction.label.name),
                )
            },
        )
    }
}
