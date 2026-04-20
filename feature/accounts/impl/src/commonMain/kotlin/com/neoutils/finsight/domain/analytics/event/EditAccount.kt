package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

class EditAccount(params: Map<String, String>) : Event("edit_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}
