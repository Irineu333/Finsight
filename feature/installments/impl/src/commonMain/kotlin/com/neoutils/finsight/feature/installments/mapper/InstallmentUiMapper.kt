package com.neoutils.finsight.feature.installments.mapper

import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.installments.screen.InstallmentWithOperationsUi
import com.neoutils.finsight.feature.transactions.model.OperationUi

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        operations: List<OperationUi>,
    ): InstallmentWithOperationsUi? {
        val sortedOperations = operations.sortedBy {
            it.operation.installment?.number ?: Int.MAX_VALUE
        }

        if (sortedOperations.isEmpty()) return null

        val firstOperation = sortedOperations.first()
        val firstCategory = firstOperation.category
        val openOperation = sortedOperations.firstOrNull {
            it.targetInvoice?.status?.isOpen == true
        }
        val currentNumber = openOperation?.operation?.installment?.number
            ?: sortedOperations.last().operation.installment?.number
            ?: installment.count
        val installmentAmount = installment.totalAmount / installment.count
        val paidCount = sortedOperations.count {
            it.targetInvoice?.status?.isPaid == true
        }
        val isActive = paidCount < installment.count

        return InstallmentWithOperationsUi(
            installment = installment,
            operations = sortedOperations,
            latestOperationDate = sortedOperations.maxOf { it.operation.date },
            title = firstOperation.operation.defaultLabel,
            categoryName = firstCategory?.name?.uppercase(),
            category = firstCategory,
            isActive = isActive,
            currentNumber = currentNumber,
            installmentAmount = installmentAmount,
            remainingAmount = (installment.count - paidCount) * installmentAmount,
            progress = currentNumber.toFloat() / installment.count,
            isDeletable = sortedOperations.all {
                val status = it.targetInvoice?.status
                status == null || status.isEditable
            },
        )
    }
}
