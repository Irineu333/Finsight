package com.neoutils.finance.database.mapper

import com.neoutils.finance.database.entity.TransactionEntity
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction

class TransactionMapper {
    fun toDomain(
        entity: TransactionEntity,
        category: Category?,
        creditCard: CreditCard?,
        invoice: Invoice?
    ): Transaction {
        return Transaction(
            id = entity.id,
            type = toDomain(entity.type),
            amount = entity.amount,
            title = entity.title,
            date = entity.date,
            category = category,
            target = toDomain(entity.target),
            creditCard = creditCard,
            invoice = invoice,
            installment = entity.installmentNumber?.let { number ->
                entity.totalInstallments?.let { total ->
                    entity.installmentGroupId?.let { groupUuid ->
                        entity.installmentTotalAmount?.let { totalAmount ->
                            Installment(
                                count = total,
                                number = number,
                                groupUuid = groupUuid,
                                totalAmount = totalAmount,
                            )
                        }
                    }
                }
            }
        )
    }

    fun toEntity(
        domain: Transaction
    ): TransactionEntity {
        return TransactionEntity(
            id = domain.id,
            type = toEntity(domain.type),
            amount = domain.amount,
            title = domain.title,
            date = domain.date,
            categoryId = domain.category?.id,
            target = toEntity(domain.target),
            creditCardId = domain.creditCard?.id,
            invoiceId = domain.invoice?.id,
            installmentNumber = domain.installment?.number,
            totalInstallments = domain.installment?.count,
            installmentGroupId = domain.installment?.groupUuid,
            installmentTotalAmount = domain.installment?.totalAmount
        )
    }

    fun toDomain(
        type: TransactionEntity.Type
    ): Transaction.Type {
        return when (type) {
            TransactionEntity.Type.EXPENSE -> Transaction.Type.EXPENSE
            TransactionEntity.Type.INCOME -> Transaction.Type.INCOME
            TransactionEntity.Type.ADJUSTMENT -> Transaction.Type.ADJUSTMENT
            TransactionEntity.Type.INVOICE_PAYMENT -> Transaction.Type.INVOICE_PAYMENT
            TransactionEntity.Type.ADVANCE_PAYMENT -> Transaction.Type.ADVANCE_PAYMENT
        }
    }

    fun toEntity(
        type: Transaction.Type
    ): TransactionEntity.Type {
        return when (type) {
            Transaction.Type.EXPENSE -> TransactionEntity.Type.EXPENSE
            Transaction.Type.INCOME -> TransactionEntity.Type.INCOME
            Transaction.Type.ADJUSTMENT -> TransactionEntity.Type.ADJUSTMENT
            Transaction.Type.INVOICE_PAYMENT -> TransactionEntity.Type.INVOICE_PAYMENT
            Transaction.Type.ADVANCE_PAYMENT -> TransactionEntity.Type.ADVANCE_PAYMENT
        }
    }

    fun toDomain(
        target: TransactionEntity.Target
    ): Transaction.Target {
        return when (target) {
            TransactionEntity.Target.ACCOUNT -> Transaction.Target.ACCOUNT
            TransactionEntity.Target.CREDIT_CARD -> Transaction.Target.CREDIT_CARD
            TransactionEntity.Target.INVOICE_PAYMENT -> Transaction.Target.INVOICE_PAYMENT
        }
    }

    fun toEntity(
        target: Transaction.Target
    ): TransactionEntity.Target {
        return when (target) {
            Transaction.Target.ACCOUNT -> TransactionEntity.Target.ACCOUNT
            Transaction.Target.CREDIT_CARD -> TransactionEntity.Target.CREDIT_CARD
            Transaction.Target.INVOICE_PAYMENT -> TransactionEntity.Target.INVOICE_PAYMENT
        }
    }
}
