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
         * denominated in a currency the app declares it no longer offers. It is also what
         * makes [retireAction] absent.
         */
        val isBase: Boolean,
        /** What names it: the same answer the refusal reads, never re-derived here. */
        val usage: CurrencyUsage,
        /**
         * Whether this screen offers deleting or archiving — the rule
         * `DeleteCurrencyUseCase` enforces, mapped through the same helper accounts,
         * cards and categories already use, so the four cannot drift.
         *
         * `null` is the base, the one row with **no** retirement at all: archiving it is
         * refused, and deleting it is refused by the account it denominates. An action
         * that is always refused is worse than one not offered — and stating the absence
         * here rather than in the composable is what keeps it testable.
         */
        val retireAction: RetireAction?,
    ) : ViewCurrencyUiState
}
