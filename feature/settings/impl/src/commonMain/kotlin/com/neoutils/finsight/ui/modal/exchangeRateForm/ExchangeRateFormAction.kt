package com.neoutils.finsight.ui.modal.exchangeRateForm

import kotlinx.datetime.LocalDate

sealed interface ExchangeRateFormAction {
    /** The priced currency. */
    data class SelectFrom(val currency: String) : ExchangeRateFormAction

    /** The currency it is priced in. */
    data class SelectTo(val currency: String) : ExchangeRateFormAction
    data class SelectDate(val date: LocalDate) : ExchangeRateFormAction
    data class ChangeRate(val rate: Double?) : ExchangeRateFormAction
    data object Submit : ExchangeRateFormAction
    data object Remove : ExchangeRateFormAction

    /** Told that no copy could be taken, the person said to remove the observation anyway. */
    data object RemoveWithoutCopy : ExchangeRateFormAction

    /** Answered no, or walked away from the question — which is the same answer. */
    data object AbandonRemoval : ExchangeRateFormAction
}
