package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Transaction

fun Transaction.signedImpact(): Double {
    return when (type) {
        Transaction.Type.INCOME -> amount
        Transaction.Type.EXPENSE -> -amount
        Transaction.Type.ADJUSTMENT -> amount
    }
}
