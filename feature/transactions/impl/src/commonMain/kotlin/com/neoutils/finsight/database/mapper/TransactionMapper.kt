package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction

class TransactionMapper {
    fun toDomain(
        entity: TransactionEntity,
        category: Category?,
        creditCard: CreditCard?,
        invoice: Invoice?,
        account: Account?
    ): Transaction {
        return Transaction(
            id = entity.id,
            operationId = entity.operationId,
            type = toDomain(entity.type),
            amount = entity.amount,
            title = entity.title,
            date = entity.date,
            category = category,
            target = toDomain(entity.target),
            creditCard = creditCard,
            invoice = invoice,
            account = account,
        )
    }

    fun toEntity(
        domain: Transaction
    ): TransactionEntity {
        return TransactionEntity(
            id = domain.id,
            operationId = domain.operationId,
            type = toEntity(domain.type),
            amount = domain.amount,
            title = domain.title,
            date = domain.date,
            categoryId = domain.category?.id,
            target = toEntity(domain.target),
            creditCardId = domain.creditCard?.id,
            invoiceId = domain.invoice?.id,
            accountId = domain.account?.id,
        )
    }

    fun toDomain(
        type: TransactionEntity.Type
    ): Transaction.Type {
        return when (type) {
            TransactionEntity.Type.EXPENSE -> Transaction.Type.EXPENSE
            TransactionEntity.Type.INCOME -> Transaction.Type.INCOME
            TransactionEntity.Type.ADJUSTMENT -> Transaction.Type.ADJUSTMENT
        }
    }

    fun toEntity(
        type: Transaction.Type
    ): TransactionEntity.Type {
        return when (type) {
            Transaction.Type.EXPENSE -> TransactionEntity.Type.EXPENSE
            Transaction.Type.INCOME -> TransactionEntity.Type.INCOME
            Transaction.Type.ADJUSTMENT -> TransactionEntity.Type.ADJUSTMENT
        }
    }

    fun toDomain(
        target: TransactionEntity.Target
    ): Transaction.Target {
        return when (target) {
            TransactionEntity.Target.ACCOUNT -> Transaction.Target.ACCOUNT
            TransactionEntity.Target.CREDIT_CARD -> Transaction.Target.CREDIT_CARD
        }
    }

    fun toEntity(
        target: Transaction.Target
    ): TransactionEntity.Target {
        return when (target) {
            Transaction.Target.ACCOUNT -> TransactionEntity.Target.ACCOUNT
            Transaction.Target.CREDIT_CARD -> TransactionEntity.Target.CREDIT_CARD
        }
    }
}
