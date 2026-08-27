package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

/**
 * The shape an invoice payment takes in the ledger, whatever decided that it may happen.
 *
 * Two legs, and only two: the money leaves the paying account **undimensioned**, and the
 * card's `LIABILITY` leg carries the invoice's dimension. A dimension on both would net
 * the invoice to zero by cancellation rather than by payment, and one copied onto the
 * conversion residue would have the whole transaction refused (design D15).
 *
 * **No rate is a parameter.** [leaving] is what the account gives up and [settling] is
 * what the invoice receives, exact in the card's currency; the rate is the quotient of
 * the two, learned afterwards and written to the archive — never to the transaction, and
 * it survives the transaction being deleted (design D11, D27). When the two ends are
 * denominated alike the quotient says nothing and nothing is archived.
 *
 * Owning the shape here is what keeps the ways in from drifting: registering a payment
 * and correcting one state the same two legs, and a form written twice diverges without
 * anything accusing it. What differs between them is only whether the legs are created
 * or rewritten — [invoke] and [rewrite] — and neither is a branch inside the shape.
 */
class WriteInvoicePaymentUseCase(
    private val transactionRepository: ITransactionRepository,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
    private val accountRepository: IAccountRepository,
) {
    /**
     * @param leaving what leaves [account], in **its** currency.
     * @param settling what the invoice receives, in the **card's** currency.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        account: Account,
        leaving: Double,
        settling: Double,
        date: LocalDate,
    ): Transaction {
        val transaction = transactionRepository.createTransaction(
            TransactionIntent(
                title = null,
                date = date,
                legs = legsOf(
                    invoice = invoice,
                    account = account,
                    leaving = leaving,
                    settling = settling,
                ),
            )
        )

        harvest(invoice, account, leaving, settling, date)

        return transaction
    }

    /**
     * The same shape, written over an operation that already exists.
     *
     * The legs are rebuilt from scratch and the transaction keeps its identity, which is
     * what separates a correction from deleting and registering another one.
     *
     * @param title what the operation goes on being called. The payment form does not
     * show this field, so what it does not exhibit it does not erase — the caller passes
     * on what the transaction carries rather than `null` out of habit.
     */
    suspend fun rewrite(
        transactionId: Long,
        title: String?,
        invoice: Invoice,
        account: Account,
        leaving: Double,
        settling: Double,
        date: LocalDate,
    ) {
        transactionRepository.updateTransaction(
            id = transactionId,
            title = title,
            date = date,
            legs = legsOf(
                invoice = invoice,
                account = account,
                leaving = leaving,
                settling = settling,
            ),
            // Two monetary legs are stated in full, so there is nothing left for the
            // boundary to complete by difference — beyond the conversion residue, which
            // it posts itself and must never carry the invoice's dimension.
            contra = null,
        )

        harvest(invoice, account, leaving, settling, date)
    }

    private fun legsOf(
        invoice: Invoice,
        account: Account,
        leaving: Double,
        settling: Double,
    ) = listOf(
        TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = leaving,
            accountId = account.id,
        ),
        TransactionLeg(
            type = TransactionType.INCOME,
            amount = settling,
            accountId = invoice.creditCard.accountId,
            dimensionId = invoice.dimensionId,
        ),
    )

    /**
     * The rate this payment applied, observed from its own two ends after the write.
     *
     * Harvesting is a side note on a payment that already landed: a card whose account
     * cannot be read still leaves the ledger correct, only the archive poorer. A
     * correction harvests the rate it applies and revokes nothing — same pair, same date
     * and same origin is the same key, and the archive replaces it by itself.
     */
    private suspend fun harvest(
        invoice: Invoice,
        account: Account,
        leaving: Double,
        settling: Double,
        date: LocalDate,
    ) {
        runCatching {
            val cardCurrency = accountRepository.currencyOf(invoice.creditCard)

            if (cardCurrency != null) {
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = settling,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }
    }
}
