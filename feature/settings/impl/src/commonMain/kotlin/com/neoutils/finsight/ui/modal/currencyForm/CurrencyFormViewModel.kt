package com.neoutils.finsight.ui.modal.currencyForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.usecase.SaveCurrencyUseCase
import com.neoutils.finsight.extension.platformCurrency
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Registering a currency and editing one are the **same write**, and this is where that
 * shows: an [existing] row seeds the fields, its absence starts them empty, and both end
 * in `save`. A seeded row is edited exactly like one the user typed — there is no
 * "built-in" and "user's", there are rows.
 *
 * **The platform suggests; it never decides.** Typing a code it recognises fills the
 * symbol and the name, both still editable, and a code it does not recognise fills
 * nothing and is registered all the same — an invented unit is precisely what this form
 * exists to allow.
 *
 * A suggestion never overwrites what the user has already written: it fills a field that
 * is still empty, or one the previous suggestion had filled.
 */
class CurrencyFormViewModel(
    private val existing: CurrencyInfo?,
    private val saveCurrency: SaveCurrencyUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CurrencyFormUiState(
            code = existing?.code.orEmpty(),
            symbol = existing?.symbol.orEmpty(),
            // The name as the row reads it — the platform's when the row stores none.
            // Leaving it as it arrived is what keeps it *unstored*: submit compares it
            // against the platform's own answer, so an untouched suggestion never
            // becomes a stored name frozen in this run's language.
            name = existing?.name.orEmpty(),
            isEditing = existing != null,
        )
    )

    val uiState = _uiState.asStateFlow()

    /** What the last suggestion filled, so that replacing it is never overwriting the user. */
    private var suggestedSymbol: String? = existing?.symbol
    private var suggestedName: String? = existing?.code?.let { platformCurrency(it)?.name }

    fun onAction(action: CurrencyFormAction) {
        when (action) {
            is CurrencyFormAction.ChangeCode -> changeCode(action.code)

            is CurrencyFormAction.ChangeSymbol -> {
                suggestedSymbol = null
                _uiState.update { it.copy(symbol = action.symbol, error = null) }
            }

            is CurrencyFormAction.ChangeName -> {
                suggestedName = null
                _uiState.update { it.copy(name = action.name, error = null) }
            }

            CurrencyFormAction.Submit -> submit()
        }
    }

    private fun changeCode(code: String) {
        val suggestion = platformCurrency(code)

        _uiState.update { state ->
            val symbol = when (state.symbol) {
                "", suggestedSymbol -> suggestion?.symbol.orEmpty()
                else -> state.symbol
            }
            val name = when (state.name) {
                "", suggestedName -> suggestion?.name.orEmpty()
                else -> state.name
            }

            suggestedSymbol = suggestion?.symbol
            suggestedName = suggestion?.name

            state.copy(code = code, symbol = symbol, name = name, error = null)
        }
    }

    private fun submit() = viewModelScope.launch {
        val state = _uiState.value

        saveCurrency(
            code = state.code,
            symbol = state.symbol,
            // A name equal to what the platform would say is not stored: storing it
            // would freeze it in the language it was suggested in, which is exactly
            // what leaving the column null avoids.
            name = state.name.takeIf { it.isNotBlank() && it != suggestedName },
            isEditing = state.isEditing,
        ).fold(
            ifLeft = { error -> _uiState.update { it.copy(error = error.toUiText()) } },
            ifRight = { modalManager.dismissAll() },
        )
    }
}
