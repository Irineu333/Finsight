package com.neoutils.finsight.ui.modal.archiveCurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ArchiveCurrency
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ArchiveCurrencyViewModel(
    private val code: String,
    private val archiveCurrency: ArchiveCurrencyUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun archive() = viewModelScope.launch {
        archiveCurrency.archive(code)
            .onRight {
                analytics.logEvent(ArchiveCurrency(code))
                modalManager.dismissAll()
            }
            // The base currency is refused here — the view screen does not offer the
            // action for it, and this is the boundary that makes that true rather than
            // merely tidy.
            .onLeft { modalManager.showError(it.toUiText()) }
    }
}
