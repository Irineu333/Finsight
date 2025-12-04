package com.neoutils.finance.data.mapper

import com.neoutils.finance.data.TransactionEntity
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

fun TransactionEntity.toDomain(
    category: Category?
): Transaction {
    return Transaction(
        id = id,
        type = type.toDomain(),
        amount = amount,
        title = title,
        date = date,
        category = category,
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        type = type.toEntity(),
        amount = amount,
        title = title,
        date = date,
        categoryId = category?.id
    )
}

fun TransactionEntity.Type.toDomain(): Transaction.Type {
    return when (this) {
        TransactionEntity.Type.EXPENSE -> Transaction.Type.EXPENSE
        TransactionEntity.Type.INCOME -> Transaction.Type.INCOME
        TransactionEntity.Type.ADJUSTMENT -> Transaction.Type.ADJUSTMENT
    }
}

fun Transaction.Type.toEntity(): TransactionEntity.Type {
    return when (this) {
        Transaction.Type.EXPENSE -> TransactionEntity.Type.EXPENSE
        Transaction.Type.INCOME -> TransactionEntity.Type.INCOME
        Transaction.Type.ADJUSTMENT -> TransactionEntity.Type.ADJUSTMENT
    }
}
