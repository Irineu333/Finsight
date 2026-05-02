package com.neoutils.finsight.feature.accounts.event

import com.neoutils.finsight.core.analytics.Event

class CreateAccount(params: Map<String, String>) : Event("create_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}
