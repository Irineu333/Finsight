package com.neoutils.finsight.ui.modal.exchangeRateForm

import kotlinx.datetime.LocalDate

sealed interface ExchangeRateFormAction {
    data class SelectCurrency(val currency: String) : ExchangeRateFormAction
    data class SelectDate(val date: LocalDate) : ExchangeRateFormAction
    data class ChangeRate(val rate: Double?) : ExchangeRateFormAction
    data object Submit : ExchangeRateFormAction
    data object Remove : ExchangeRateFormAction
}
