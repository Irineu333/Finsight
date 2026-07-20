package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.screen.installments.InstallmentWithTransactionsUi

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        transactions: List<Transaction>,
    ): InstallmentWithTransactionsUi? {
        val sortedTransactions = transactions.sortedBy { it.installment?.number ?: Int.MAX_VALUE }

        if (sortedTransactions.isEmpty()) return null

        val firstTransaction = sortedTransactions.first()
        val openTransaction = sortedTransactions.firstOrNull {
            it.targetInvoice?.status?.isOpen == true
        }
        val currentNumber = openTransaction?.installment?.number
            ?: sortedTransactions.last().installment?.number
            ?: installment.count
        val installmentAmount = installment.totalAmount / installment.count
        val paidCount = sortedTransactions.count {
            it.targetInvoice?.status?.isPaid == true
        }
        val isActive = paidCount < installment.count

        return InstallmentWithTransactionsUi(
            installment = installment,
            transactions = sortedTransactions,
            latestTransactionDate = sortedTransactions.maxOf { it.date },
            title = firstTransaction.displayTitle,
            categoryName = firstTransaction.category?.name?.uppercase(),
            category = firstTransaction.category,
            isActive = isActive,
            currentNumber = currentNumber,
            installmentAmount = installmentAmount,
            remainingAmount = (installment.count - paidCount) * installmentAmount,
            progress = currentNumber.toFloat() / installment.count,
            isDeletable = sortedTransactions.all {
                it.targetInvoice?.status?.isEditable != false
            },
        )
    }
}
