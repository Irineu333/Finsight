package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IOperationRepository {
    fun observeAllOperations(): Flow<List<Operation>>

    fun observeOperationsBy(
        date: LocalDate? = null,
        invoiceId: Long? = null,
        creditCardId: Long? = null,
        accountId: Long? = null,
    ): Flow<List<Operation>>

    fun observeOperationById(id: Long): Flow<Operation?>

    suspend fun getAllOperations(): List<Operation>
    suspend fun getOperationById(id: Long): Operation?

    /** Writes the user's [intent] as a balanced set of ledger entries. */
    suspend fun createOperation(intent: OperationIntent): Operation

    /**
     * Writes several intents as one unit. An installment is a single decision by
     * the user, so its N operations must be all-or-nothing: writing 7 of 12 and
     * failing would leave an installment describing money that was never recorded.
     */
    suspend fun createOperations(intents: List<OperationIntent>): List<Operation>

    /** Rewrites the operation's row and its ledger legs from the edited [leg]. */
    suspend fun updateOperation(id: Long, title: String?, date: LocalDate, leg: OperationLeg)

    suspend fun deleteOperationById(id: Long)
    suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long)
}
