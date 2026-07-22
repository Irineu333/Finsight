package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.Recurring

class TransactionMapper {

    /**
     * Builds the domain [Transaction] from its row plus its hydrated ledger legs.
     * Everything about *what the transaction is* comes from the entries — the row
     * only carries what the ledger cannot express (title, date, and the
     * recurring/installment links).
     *
     * [categoriesByDimension] and [invoices] are both resolved from the dimension a
     * leg carries, which is why the leg matters: an invoice dimension only ever
     * lands on the `LIABILITY` leg and a category dimension only on the nominal one
     * (the write boundary enforces it), so each lookup reads its own leg and the two
     * never see each other's dimension.
     */
    fun toDomain(
        entity: TransactionEntity,
        categoriesByDimension: Map<Long, Category>,
        creditCards: Map<Long, CreditCard>,
        invoices: Map<Long, Invoice>,
        installments: Map<Long, Installment>,
        recurring: Map<Long, Recurring>,
        entries: List<Entry>,
    ): Transaction? {
        if (entries.isEmpty()) return null

        val assetEntries = entries.filter { it.account.type == AccountType.ASSET }
        val cardEntry = entries.firstOrNull { it.account.type == AccountType.LIABILITY }
        val nominalEntry = entries.firstOrNull { it.account.type.isNominal }

        return Transaction(
            id = entity.id,
            title = entity.title,
            date = entity.date,
            recurring = entity.recurringId?.let { recurringId ->
                entity.recurringCycle?.let { cycleNumber ->
                    recurring[recurringId]?.let { instance ->
                        TransactionRecurring(instance = instance, cycleNumber = cycleNumber)
                    }
                }
            },
            category = nominalEntry?.dimensionId?.let { categoriesByDimension[it] },
            // The money-out ASSET leg is the source; with a single leg it is that leg.
            sourceAccount = (assetEntries.minByOrNull { it.amount } ?: assetEntries.firstOrNull())?.account,
            targetCreditCard = cardEntry?.account?.id?.let { accountId ->
                creditCards.values.firstOrNull { it.accountId == accountId }
            },
            // The invoice is reached from the dimension its liability leg carries.
            targetInvoice = cardEntry?.dimensionId
                ?.let { dimensionId -> invoices.values.firstOrNull { it.dimensionId == dimensionId } },
            installment = entity.installmentNumber?.let { number ->
                entity.installmentId?.let { installmentId ->
                    installments[installmentId]?.let { instance ->
                        TransactionInstallment(instance = instance, number = number)
                    }
                }
            },
            entries = entries,
        )
    }
}
