package com.neoutils.finance.domain.exception

import com.neoutils.finance.util.UiText
import kotlinx.coroutines.runBlocking

open class AccountException(val text: UiText) : Exception() {

    constructor(message: String) : this(UiText.Raw(message))

    override val message get() = runBlocking { text.asString() }
}
