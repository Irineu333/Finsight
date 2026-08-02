package com.neoutils.finsight.ui.modal.exchangeRateForm

import com.neoutils.finsight.domain.model.CurrencyInfo
import kotlinx.datetime.LocalDate

data class ExchangeRateFormUiState(
    /** The currency being priced — the one the rate answers *how much* of. */
    val from: String,
    /** The currency [from] is priced **in**. Pre-selected with the base in force. */
    val to: String,
    val date: LocalDate,
    val rate: Double?,
    /** Editing an existing rate rather than registering a new one. */
    val isEditing: Boolean,
    /**
     * The whole catalog, on **both** ends.
     *
     * The base used to be filtered out, and with the pair explicit that stops making
     * sense: pricing the base itself against another currency is a legitimate
     * observation, and its inverse feeds the reading. What remains is the one restriction
     * that is really one — a currency against itself says nothing.
     */
    val selectableCurrencies: List<CurrencyInfo>,
) {
    val canSubmit get() = rate != null && from != to
}
