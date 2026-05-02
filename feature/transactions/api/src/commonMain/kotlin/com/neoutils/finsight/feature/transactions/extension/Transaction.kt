package com.neoutils.finsight.feature.transactions.extension

import com.neoutils.finsight.feature.transactions.model.Transaction
fun Transaction.signedImpact(): Double {
    return when (type) {
        Transaction.Type.INCOME -> amount
        Transaction.Type.EXPENSE -> -amount
        Transaction.Type.ADJUSTMENT -> amount
    }
}
