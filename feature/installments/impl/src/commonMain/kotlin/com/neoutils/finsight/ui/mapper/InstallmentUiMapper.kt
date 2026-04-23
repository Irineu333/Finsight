package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.screen.installments.InstallmentWithOperationsUi

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        operations: List<Operation>,
    ): InstallmentWithOperationsUi? {
        val sortedOperations = operations.sortedBy { it.installment?.number ?: Int.MAX_VALUE }

        if (sortedOperations.isEmpty()) return null

        val firstOperation = sortedOperations.first()
        val openOperation = sortedOperations.firstOrNull {
            it.targetInvoice?.status?.isOpen == true
        }
        val currentNumber = openOperation?.installment?.number
            ?: sortedOperations.last().installment?.number
            ?: installment.count
        val installmentAmount = installment.totalAmount / installment.count
        val paidCount = sortedOperations.count {
            it.targetInvoice?.status?.isPaid == true
        }
        val isActive = paidCount < installment.count

        return InstallmentWithOperationsUi(
            installment = installment,
            operations = sortedOperations,
            latestOperationDate = sortedOperations.maxOf { it.date },
            title = firstOperation.label,
            categoryName = firstOperation.category?.name?.uppercase(),
            category = firstOperation.category,
            isActive = isActive,
            currentNumber = currentNumber,
            installmentAmount = installmentAmount,
            remainingAmount = (installment.count - paidCount) * installmentAmount,
            progress = currentNumber.toFloat() / installment.count,
            isDeletable = sortedOperations.all {
                it.targetInvoice?.status?.isEditable != false
            },
        )
    }
}
