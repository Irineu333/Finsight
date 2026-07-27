package com.neoutils.finsight.ui.modal.launchYield

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed interface LaunchYieldUiState {

    data object Loading : LaunchYieldUiState

    /**
     * A yield is described by two things and no more: when it landed and how much.
     * There is no target balance here, and no previous launch to reconcile with —
     * every submission is a new transaction (design D1).
     */
    data class Content(
        val account: Account,
        val date: LocalDate,
        val isSubmitting: Boolean = false,
    ) : LaunchYieldUiState
}
