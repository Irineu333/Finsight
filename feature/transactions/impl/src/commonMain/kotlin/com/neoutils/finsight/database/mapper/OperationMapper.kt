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
            kind = toDomain(entity.kind),
            title = entity.title ?: primaryTransaction.title,
            date = primaryTransaction.date,
            recurring = entity.recurringId?.let { recurringId ->
                entity.recurringCycle?.let { cycleNumber ->
                    recurring[recurringId]?.let { instance ->
                        OperationRecurring(
                            id = instance.id,
                            recurringLabel = instance.label,
                            cycleNumber = cycleNumber,
                        )
                    }
                }
            },
            category = entity.categoryId?.let { categories[it] } ?: primaryTransaction.category,
            sourceAccount = entity.sourceAccountId?.let { accounts[it] },
            targetCreditCard = entity.targetCreditCardId?.let { creditCards[it] },
            targetInvoice = entity.targetInvoiceId?.let { invoices[it] },
            installment = entity.installmentNumber?.let { number ->
                entity.installmentId?.let { installmentId ->
                    installments[installmentId]?.let { instance ->
                        OperationInstallment(
                            id = instance.id,
                            count = instance.count,
                            number = number,
                            totalAmount = instance.totalAmount,
                        )
                    }
                }
            },
            transactions = transactions.sortedByDescending { it.id },
        )
    }

    fun toDomain(kind: OperationEntity.Kind): Operation.Kind {
        return when (kind) {
            OperationEntity.Kind.TRANSACTION -> Operation.Kind.TRANSACTION
            OperationEntity.Kind.PAYMENT -> Operation.Kind.PAYMENT
            OperationEntity.Kind.TRANSFER -> Operation.Kind.TRANSFER
        }
    }

    fun toEntity(kind: Operation.Kind): OperationEntity.Kind {
        return when (kind) {
            Operation.Kind.TRANSACTION -> OperationEntity.Kind.TRANSACTION
            Operation.Kind.PAYMENT -> OperationEntity.Kind.PAYMENT
            Operation.Kind.TRANSFER -> OperationEntity.Kind.TRANSFER
        }
    }
}
