package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A base currency that does not move.
 *
 * `CreditCardRepository` reads the base to answer what a **new** card is pre-selected
 * with, and the suites here exercise archiving and invoice lookup — neither asks that
 * question. So the preference is a constant rather than something to observe: what these
 * tests need from it is that it exists, not that it changes.
 */
internal class FixedBaseCurrency(base: String) : IBaseCurrencyRepository {

    private val state = MutableStateFlow(base)

    override fun observe(): StateFlow<String> = state

}
