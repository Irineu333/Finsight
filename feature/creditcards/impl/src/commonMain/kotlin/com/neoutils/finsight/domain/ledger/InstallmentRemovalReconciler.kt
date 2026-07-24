package com.neoutils.finsight.domain.ledger

import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInstallmentRepository

/**
 * Keeps an installment describing the transactions that still exist.
 *
 * `Installment` holds a `count` and a `totalAmount` that are copies of facts about
 * its transactions, so removing one of them leaves the copy wrong — "3 of 6" over
 * five rows, a progress bar over a six that is gone. Nothing in the ledger notices:
 * every balance stays correct, only the installment lies.
 *
 * The copy is not redundant, which is why it is corrected rather than derived:
 * `totalAmount` is the total the *user declared*, and the per-share rounding means
 * Σ of the shares need not equal it — R$ 100,00 in three is 3 × 33,33. Deriving it
 * would change a rendered figure, which this change does not do.
 *
 * It runs inside the ledger's own write transaction, through
 * [TransactionRemovalHook], because all three removal paths reach it: one
 * installment transaction, a whole installment, and a future invoice's transactions.
 */
class InstallmentRemovalReconciler(
    private val installmentRepository: IInstallmentRepository,
    private val installmentDao: InstallmentDao,
) : TransactionRemovalHook {

    override suspend fun onRemoved(transaction: Transaction) {
        val installmentId = transaction.installmentId ?: return

        // Counted, not decremented from the stored value: the rows are the fact, and
        // the copy is what we are here to fix.
        val remaining = installmentDao.countTransactions(installmentId)
        if (remaining <= 0) {
            installmentRepository.deleteInstallmentById(installmentId)
            return
        }

        val installment = installmentRepository.getInstallmentById(installmentId) ?: return
        // This transaction's own share, from the ledger: the money that left.
        val share = transaction.entries.filter { it.amount < 0 }.sumOf { -it.amount } / 100.0
        installmentRepository.updateInstallment(
            id = installmentId,
            count = remaining,
            totalAmount = installment.totalAmount - share,
        )
    }
}
