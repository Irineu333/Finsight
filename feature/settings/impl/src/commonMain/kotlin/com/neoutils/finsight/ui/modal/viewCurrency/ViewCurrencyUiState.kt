package com.neoutils.finsight.ui.modal.viewCurrency

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.usecase.CurrencyUsage
import com.neoutils.finsight.ui.model.RetireAction

sealed interface ViewCurrencyUiState {

    data object Loading : ViewCurrencyUiState

    data object Error : ViewCurrencyUiState

    data class Content(
        val currency: CurrencyInfo,
        val isArchived: Boolean,
        /**
         * The base is not archivable — archiving it would leave every consolidated figure
         * denominated in a currency the app declares it no longer offers. The screen
         * offers no action it will always refuse.
         */
        val isBase: Boolean,
        /** What names it: the same answer the refusal reads, never re-derived here. */
        val usage: CurrencyUsage,
        /**
         * Whether this screen offers deleting or archiving — the rule
         * `DeleteCurrencyUseCase` enforces, mapped through the same helper accounts,
         * cards and categories already use, so the four cannot drift.
         */
        val retireAction: RetireAction,
    ) : ViewCurrencyUiState
}
