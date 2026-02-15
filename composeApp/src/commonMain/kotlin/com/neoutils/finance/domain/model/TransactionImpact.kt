package com.neoutils.finance.domain.model

fun Transaction.signedImpact(): Double {
    return when (type) {
        Transaction.Type.INCOME -> amount
        Transaction.Type.EXPENSE -> -amount
        Transaction.Type.ADJUSTMENT -> amount
    }
}
