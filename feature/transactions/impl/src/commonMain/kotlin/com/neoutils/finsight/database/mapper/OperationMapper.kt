package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.OperationInstallment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationRecurring
import com.neoutils.finsight.domain.model.Recurring

class OperationMapper {

    /**
     * Builds the domain [Operation] from its row plus its hydrated ledger legs.
     * Everything about *what the operation is* comes from the entries — the row
     * only carries what the ledger cannot express (title, date, the user's
     * category choice, and the recurring/installment links).
     */
    fun toDomain(
        entity: TransactionEntity,
        categories: Map<Long, Category>,
        creditCards: Map<Long, CreditCard>,
        invoices: Map<Long, Invoice>,
        installments: Map<Long, Installment>,
        recurring: Map<Long, Recurring>,
        entries: List<Entry>,
    ): Operation? {
        if (entries.isEmpty()) return null

        val assetEntries = entries.filter { it.account.type == AccountType.ASSET }
        val cardAccountId = entries.firstOrNull { it.account.type == AccountType.LIABILITY }?.account?.id

        return Operation(
            id = entity.id,
            title = entity.title,
            date = entity.date,
            recurring = entity.recurringId?.let { recurringId ->
                entity.recurringCycle?.let { cycleNumber ->
                    recurring[recurringId]?.let { instance ->
                        OperationRecurring(instance = instance, cycleNumber = cycleNumber)
                    }
                }
            },
            category = entity.categoryId?.let { categories[it] },
            // The money-out ASSET leg is the source; with a single leg it is that leg.
            sourceAccount = (assetEntries.minByOrNull { it.amount } ?: assetEntries.firstOrNull())?.account,
            targetCreditCard = cardAccountId?.let { accountId ->
                creditCards.values.firstOrNull { it.accountId == accountId }
            },
            targetInvoice = entries.firstNotNullOfOrNull { it.invoiceId }?.let { invoices[it] },
            installment = entity.installmentNumber?.let { number ->
                entity.installmentId?.let { installmentId ->
                    installments[installmentId]?.let { instance ->
                        OperationInstallment(instance = instance, number = number)
                    }
                }
            },
            entries = entries,
        )
    }
}
