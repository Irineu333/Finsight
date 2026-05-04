package com.neoutils.finsight.feature.installments.mapper

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.installments.screen.InstallmentWithOperationsUi

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        operations: List<Operation>,
        invoicesById: Map<Long, Invoice> = emptyMap(),
        categoriesById: Map<Long, Category> = emptyMap(),
    ): InstallmentWithOperationsUi? {
        val sortedOperations = operations.sortedBy { it.installment?.number ?: Int.MAX_VALUE }

        if (sortedOperations.isEmpty()) return null

        val firstOperation = sortedOperations.first()
        val firstCategory = firstOperation.categoryId?.let { categoriesById[it] }
        val openOperation = sortedOperations.firstOrNull {
            it.targetInvoiceId?.let { id -> invoicesById[id]?.status?.isOpen } == true
        }
        val currentNumber = openOperation?.installment?.number
            ?: sortedOperations.last().installment?.number
            ?: installment.count
        val installmentAmount = installment.totalAmount / installment.count
        val paidCount = sortedOperations.count {
            it.targetInvoiceId?.let { id -> invoicesById[id]?.status?.isPaid } == true
        }
        val isActive = paidCount < installment.count

        return InstallmentWithOperationsUi(
            installment = installment,
            operations = sortedOperations,
            latestOperationDate = sortedOperations.maxOf { it.date },
            title = firstOperation.defaultLabel,
            categoryName = firstCategory?.name?.uppercase(),
            category = firstCategory,
            isActive = isActive,
            currentNumber = currentNumber,
            installmentAmount = installmentAmount,
            remainingAmount = (installment.count - paidCount) * installmentAmount,
            progress = currentNumber.toFloat() / installment.count,
            isDeletable = sortedOperations.all {
                it.targetInvoiceId?.let { id -> invoicesById[id]?.status?.isEditable } != false
            },
        )
    }
}
