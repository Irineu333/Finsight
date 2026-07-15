package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.OperationEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.OperationInstallment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

class OperationMapper {

    fun toDomain(
        entity: OperationEntity,
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        creditCards: Map<Long, CreditCard>,
        invoices: Map<Long, Invoice>,
        installments: Map<Long, Installment>,
        accounts: Map<Long, Account>,
        recurring: Map<Long, Recurring>,
    ): Operation? {
        if (transactions.isEmpty()) return null

        val primaryTransaction = transactions
            .firstOrNull { it.target == Transaction.Target.ACCOUNT }
            ?: transactions.first()

        return Operation(
            id = entity.id,
            title = entity.title ?: primaryTransaction.title,
            date = primaryTransaction.date,
            recurring = entity.recurringId?.let { recurringId ->
                entity.recurringCycle?.let { cycleNumber ->
                    recurring[recurringId]?.let { instance ->
                        OperationRecurring(
                            instance = instance,
                            cycleNumber = cycleNumber,
                        )
                    }
                }
            },
            category = entity.categoryId?.let { categories[it] } ?: primaryTransaction.category,
            // Derived from the legs (the denormalized pointer columns were removed):
            // source = the money-out account leg (else any account leg); card/invoice
            // from the card leg.
            sourceAccount = transactions.firstOrNull { it.account != null && it.type == Transaction.Type.EXPENSE }?.account
                ?: transactions.firstOrNull { it.account != null }?.account,
            targetCreditCard = transactions.firstNotNullOfOrNull { it.creditCard },
            targetInvoice = transactions.firstNotNullOfOrNull { it.invoice },
            installment = entity.installmentNumber?.let { number ->
                entity.installmentId?.let { installmentId ->
                    installments[installmentId]?.let { instance ->
                        OperationInstallment(
                            instance = instance,
                            number = number,
                        )
                    }
                }
            },
            transactions = transactions.sortedByDescending { it.id },
        )
    }
}
