package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

class CreateAccount(params: Map<String, String>) : Event("create_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}

class EditAccount(params: Map<String, String>) : Event("edit_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}

object DeleteAccount : Event("delete_account")

object AdjustAccountBalance : Event("adjust_account_balance")

object TransferBetweenAccounts : Event("transfer_between_accounts")
