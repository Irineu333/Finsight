package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.ui.screen.installments.InstallmentTransactionUi
import com.neoutils.finsight.ui.screen.installments.InstallmentWithTransactionsUi

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        transactions: List<Transaction>,
    ): InstallmentWithTransactionsUi? {
        val sortedTransactions = transactions.sortedBy { it.installment?.number ?: Int.MAX_VALUE }

        if (sortedTransactions.isEmpty()) return null

        val firstTransaction = sortedTransactions.first()
        val category = firstTransaction.category
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
            installmentId = installment.id,
            latestTransactionDate = sortedTransactions.maxOf { it.date },
            title = firstTransaction.displayTitle,
            categoryName = category?.name?.uppercase(),
            categoryIcon = category?.icon,
            categoryType = category?.type,
            isCategoryArchived = category?.isArchived == true,
            isActive = isActive,
            currentNumber = currentNumber,
            totalCount = installment.count,
            totalAmount = installment.totalAmount,
            installmentAmount = installmentAmount,
            remainingAmount = (installment.count - paidCount) * installmentAmount,
            progress = currentNumber.toFloat() / installment.count,
            // Deleting an installment erases every one of its transactions, so each
            // invoice must still accept edits (`Invoice.Status.isEditable` — not
            // `isDeletable`, which is about deleting the invoice itself). An invoice
            // that could not be resolved cannot be shown to accept it: fail closed.
            isDeletable = sortedTransactions.all {
                it.targetInvoice?.status?.isEditable == true
            },
        )
    }

    /**
     * The row of a single installment transaction. Returns `null` when the
     * transaction has no leg to look through, so the caller omits it — the same
     * contract as [toTransactionUi].
     */
    fun toRowUi(transaction: Transaction): InstallmentTransactionUi? {
        val transactionUi = transaction.toTransactionUi() ?: return null

        return InstallmentTransactionUi(
            transaction = transactionUi,
            isSettled = when (transaction.targetInvoice?.status) {
                Invoice.Status.PAID,
                Invoice.Status.RETROACTIVE -> true

                else -> false
            },
        )
    }
}
