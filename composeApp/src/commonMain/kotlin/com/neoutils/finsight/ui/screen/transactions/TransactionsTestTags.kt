package com.neoutils.finsight.ui.screen.transactions

object TransactionsTestTags {
    const val ROOT = "transactions-root"
    const val FAB = "transactions-fab"

    fun item(operationId: Long): String = "transactions-item-$operationId"
}
