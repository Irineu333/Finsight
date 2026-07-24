package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.ui.screen.installments.InstallmentTransactionUi
import com.neoutils.finsight.ui.screen.installments.InstallmentUi

/**
 * The invoice a leg belongs to, reached through the dimension its `LIABILITY` leg
 * carries. The ledger hands out that identity and nothing else (design D6), so this
 * feature — which owns invoices — is where it becomes one.
 */
internal fun Map<Long, Invoice>.invoiceOf(transaction: Transaction): Invoice? =
    transaction.liabilityDimensionId?.let { this[it] }

class InstallmentUiMapper {

    fun toUi(
        installment: Installment,
        transactions: List<Transaction>,
        lookup: TransactionFacadeLookup,
        invoicesByDimension: Map<Long, Invoice>,
    ): InstallmentUi? {
        val sortedTransactions = transactions.sortedBy { it.installmentNumber ?: Int.MAX_VALUE }

        if (sortedTransactions.isEmpty()) return null

        val firstTransaction = sortedTransactions.first()
        val category = lookup.categoryOf(firstTransaction)
        val openTransaction = sortedTransactions.firstOrNull {
            invoicesByDimension.invoiceOf(it)?.status?.isOpen == true
        }
        val currentNumber = openTransaction?.installmentNumber
            ?: sortedTransactions.last().installmentNumber
            ?: installment.count
        val installmentAmount = installment.totalAmount / installment.count
        val paidCount = sortedTransactions.count {
            invoicesByDimension.invoiceOf(it)?.status?.isPaid == true
        }
        val isActive = paidCount < installment.count

        return InstallmentUi(
            installmentId = installment.id,
            latestTransactionDate = sortedTransactions.maxOf { it.date },
            title = displayTitleOf(firstTransaction.title, category),
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
                invoicesByDimension.invoiceOf(it)?.status?.isEditable == true
            },
        )
    }

    /**
     * The row of a single installment transaction. Returns `null` when the
     * transaction has no leg to look through, so the caller omits it — the same
     * contract as [toTransactionUi].
     */
    fun toRowUi(
        transaction: Transaction,
        lookup: TransactionFacadeLookup,
        invoicesByDimension: Map<Long, Invoice>,
    ): InstallmentTransactionUi? {
        val transactionUi = transaction.toTransactionUi(lookup = lookup) ?: return null

        return InstallmentTransactionUi(
            transaction = transactionUi,
            isSettled = when (invoicesByDimension.invoiceOf(transaction)?.status) {
                Invoice.Status.PAID,
                Invoice.Status.RETROACTIVE -> true

                else -> false
            },
        )
    }
}
