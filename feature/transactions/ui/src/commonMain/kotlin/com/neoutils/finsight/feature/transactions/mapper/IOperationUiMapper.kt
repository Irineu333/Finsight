package com.neoutils.finsight.feature.transactions.mapper

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.model.TransactionUi

interface IOperationUiMapper {
    suspend fun toUi(
        transaction: Transaction,
    ): TransactionUi

    suspend fun toUi(
        operation: Operation,
        perspective: OperationPerspective,
    ): OperationUi

    suspend fun toUi(
        operations: List<Operation>,
        perspective: OperationPerspective,
    ): List<OperationUi>
}
