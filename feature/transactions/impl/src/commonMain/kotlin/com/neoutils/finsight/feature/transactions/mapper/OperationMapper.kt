package com.neoutils.finsight.feature.transactions.mapper

import com.neoutils.finsight.core.database.entity.OperationEntity
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.transactions.model.OperationInstallment
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationRecurring
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction

class OperationMapper {

    fun toDomain(
        entity: OperationEntity,
        transactions: List<Transaction>,
        installments: Map<Long, Installment>,
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
            categoryId = entity.categoryId ?: primaryTransaction.categoryId,
            sourceAccountId = entity.sourceAccountId,
            targetCreditCardId = entity.targetCreditCardId,
            targetInvoiceId = entity.targetInvoiceId,
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
