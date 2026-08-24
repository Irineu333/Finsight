package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryOverview
import com.neoutils.finsight.ui.model.RetireAction

sealed interface ViewCategoryUiState {

    data object Loading : ViewCategoryUiState

    data object Error : ViewCategoryUiState

    data class Content(
        val category: Category,
        // Which retire action this screen may offer — the same rule accounts and
        // cards use, so the three facades cannot drift.
        val retireAction: RetireAction,
        /**
         * The figures, already decided: which one is the highlight, over what window, and
         * whether a variation exists are answers this state carries rather than questions
         * the screen asks. A category has no currency of its own — it is a dimension, and
         * its entries may sit in several (design D13) — so every figure in here is a
         * consolidated one and never a number wearing the base currency (design D29).
         */
        val overview: CategoryOverview,
    ) : ViewCategoryUiState
}
