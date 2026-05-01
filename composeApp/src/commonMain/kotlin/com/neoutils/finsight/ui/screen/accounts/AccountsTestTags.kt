package com.neoutils.finsight.ui.screen.accounts

object AccountsTestTags {
    const val ROOT = "accounts-root"
    const val FAB = "accounts-fab"
    const val TRANSFER_ACTION = "accounts-transfer-action"
    const val EDIT_BALANCE_ACTION = "accounts-edit-balance"

    fun item(accountId: Long): String = "accounts-item-$accountId"
}
