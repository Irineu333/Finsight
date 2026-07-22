package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * The facades a transactions screen renders around a ledger transaction.
 *
 * The ledger hands out identities — an account id on the `LIABILITY` leg, a
 * dimension on that leg and on the nominal one, and the installment/recurring ids
 * on the row — and stops there (design D6). Turning them into something with a name
 * is the consuming feature's job, and this is where *this* feature does it, once,
 * for the three screens that need the same answer.
 */
data class TransactionFacades(
    val category: Category? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
    val installment: TransactionInstallment? = null,
    val recurring: TransactionRecurring? = null,
)

fun interface TransactionFacadeResolver {
    suspend fun resolve(transaction: Transaction): TransactionFacades
}

class LedgerTransactionFacadeResolver(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val installmentRepository: IInstallmentRepository,
    private val recurringRepository: IRecurringRepository,
) : TransactionFacadeResolver {
    /**
     * Closed facades included, everywhere: this resolves a *historical* reference,
     * not an offer of a choice. A transaction on an archived card must keep showing
     * the card's name — dropping it would read as if the link had been erased.
     */
    override suspend fun resolve(transaction: Transaction) = TransactionFacades(
        category = transaction.categoryDimensionId?.let { dimensionId ->
            categoryRepository.getAllCategoriesIncludingClosed().firstOrNull { it.dimensionId == dimensionId }
        },
        creditCard = transaction.cardAccountId?.let { accountId ->
            creditCardRepository.getAllCreditCardsIncludingClosed().firstOrNull { it.accountId == accountId }
        },
        invoice = transaction.invoiceDimensionId?.let { dimensionId ->
            invoiceRepository.getAllInvoices().firstOrNull { it.dimensionId == dimensionId }
        },
        installment = transaction.installmentNumber?.let { number ->
            transaction.installmentId
                ?.let { installmentRepository.getInstallmentById(it) }
                ?.let { TransactionInstallment(instance = it, number = number) }
        },
        recurring = transaction.recurringCycle?.let { cycle ->
            transaction.recurringId
                ?.let { recurringRepository.getRecurringById(it) }
                ?.let { TransactionRecurring(instance = it, cycleNumber = cycle) }
        },
    )
}
