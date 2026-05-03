package com.neoutils.finsight.core.domain.extension

import com.neoutils.finsight.core.domain.model.Transaction

fun Transaction.signedImpact(): Double {
    return when (type) {
        Transaction.Type.INCOME -> amount
        Transaction.Type.EXPENSE -> -amount
        Transaction.Type.ADJUSTMENT -> amount
    }
}
