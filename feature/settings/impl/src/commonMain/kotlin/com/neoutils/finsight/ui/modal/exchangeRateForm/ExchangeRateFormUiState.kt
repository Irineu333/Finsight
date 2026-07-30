package com.neoutils.finsight.ui.modal.exchangeRateForm

import com.neoutils.finsight.domain.model.CurrencyInfo
import kotlinx.datetime.LocalDate

data class ExchangeRateFormUiState(
    val baseCurrency: String,
    val currency: String,
    val date: LocalDate,
    val rate: Double?,
    /** Editing an existing rate rather than registering a new one. */
    val isEditing: Boolean,
    /** The catalog minus the base: a rate of the base against itself says nothing. */
    val selectableCurrencies: List<CurrencyInfo>,
) {
    val canSubmit get() = rate != null && currency != baseCurrency
}
