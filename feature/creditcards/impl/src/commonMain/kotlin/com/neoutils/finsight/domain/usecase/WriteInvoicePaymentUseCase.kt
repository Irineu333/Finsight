package com.neoutils.finsight.domain.usecase

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
 * Owning the shape here is what keeps the two payment modes from drifting: a form
 * written twice diverges without anything accusing it.
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
                legs = listOf(
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
                ),
            )
        )

        // Harvesting is a side note on a payment that already landed: a card whose
        // account cannot be read still leaves the ledger correct, only the archive
        // poorer.
        runCatching {
            val cardCurrency = accountRepository
                .getAccountById(invoice.creditCard.accountId)
                ?.currency

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

        return transaction
    }
}
