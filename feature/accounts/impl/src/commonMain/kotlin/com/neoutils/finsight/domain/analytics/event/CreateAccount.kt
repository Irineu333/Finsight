package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

class CreateAccount(params: Map<String, String>) : Event("create_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}
