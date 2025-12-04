package com.neoutils.finance.data.mapper

import com.neoutils.finance.data.entity.TransactionEntity
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

class TransactionMapper {
    fun toDomain(
        entity: TransactionEntity,
        category: Category?
    ): Transaction {
        return Transaction(
            id = entity.id,
            type = toDomain(entity.type),
            amount = entity.amount,
            title = entity.title,
            date = entity.date,
            category = category,
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
            categoryId = domain.category?.id
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
}
