package com.neoutils.finsight.ui.screen.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.ui.model.toArchivedUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ArchivedCreditCardsViewModel(
    creditCardRepository: ICreditCardRepository,
) : ViewModel() {

    val uiState = creditCardRepository.observeAllCreditCardsIncludingClosed()
        .map { cards -> cards.filter(CreditCard::isArchived) }
        .map { archived ->
            if (archived.isEmpty()) {
                ArchivedCreditCardsUiState.Empty
            } else {
                ArchivedCreditCardsUiState.Content(archived.map(CreditCard::toArchivedUi))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArchivedCreditCardsUiState.Loading,
        )
}
