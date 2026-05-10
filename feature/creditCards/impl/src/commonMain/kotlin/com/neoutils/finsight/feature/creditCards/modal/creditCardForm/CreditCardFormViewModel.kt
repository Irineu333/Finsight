package com.neoutils.finsight.feature.creditCards.modal.creditCardForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.extension.CurrencyFormatter
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.utils.util.DebounceManager
import com.neoutils.finsight.core.utils.util.ObservableMutableMap
import com.neoutils.finsight.feature.creditCards.error.CreditCardError
import com.neoutils.finsight.feature.creditCards.event.CreateCreditCard
import com.neoutils.finsight.feature.creditCards.event.EditCreditCard
import com.neoutils.finsight.feature.creditCards.exception.CreditCardException
import com.neoutils.finsight.feature.creditCards.extension.toUiText
import com.neoutils.finsight.feature.creditCards.model.form.CreditCardForm
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.usecase.AddCreditCardUseCase
import com.neoutils.finsight.feature.creditCards.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.feature.creditCards.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.feature.creditCards.util.CreditCardPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CreditCardFormViewModel(
    private val creditCardId: Long?,
    private val formatter: CurrencyFormatter,
    private val creditCardRepository: ICreditCardRepository,
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val creditCardPeriod: CreditCardPeriod,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = creditCardId != null

    private val validation = ObservableMutableMap<CreditCardField, Validation>(
        map = mutableMapOf(
            CreditCardField.NAME to Validation.Valid
        )
    )

    private val form = MutableStateFlow<CreditCardForm?>(null)
    private val notFound = MutableStateFlow(false)

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {

        if (creditCardId == null) {
            form.value = CreditCardForm(iconKey = AppIcon.CARD.key)
            return@launch
        }

        val creditCard = creditCardRepository.getCreditCardById(creditCardId)

        if (creditCard == null) {
            crashlytics.recordException(CreditCardException(CreditCardError.NOT_FOUND))
            notFound.value = true
            return@launch
        }

        form.value = CreditCardForm(
            name = creditCard.name,
            limit = formatter.format(creditCard.limit),
            closingDayUser = creditCard.closingDay.toString(),
            dueDayUser = creditCard.dueDay.toString(),
            closingDayCalc = creditCardPeriod.calculateClosingDay(creditCard.dueDay),
            dueDayCalc = creditCardPeriod.calculateDueDay(creditCard.closingDay),
            iconKey = creditCard.iconKey,
        )
    }

    private val content = combine(
        form.filterNotNull(),
        validation,
    ) { form, validation ->
        CreditCardFormUiState.Content(
            form = form,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.isValid() &&
                validation[CreditCardField.NAME] == Validation.Valid,
        ) as CreditCardFormUiState
    }

    val uiState = notFound.flatMapLatest { error ->
        if (error) flowOf(CreditCardFormUiState.Error) else content
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardFormUiState.Loading,
    )

    fun onAction(action: CreditCardFormAction) {
        when (action) {
            is CreditCardFormAction.NameChanged -> changeName(action.name)
            is CreditCardFormAction.LimitChanged -> {
                form.update { it?.copy(limit = action.limit) }
            }

            is CreditCardFormAction.ClosingDayChanged -> {
                val dueCalc = action.closingDay.toIntOrNull()
                    ?.let { creditCardPeriod.calculateDueDay(it) }
                form.update {
                    it?.copy(
                        closingDayUser = action.closingDay,
                        dueDayCalc = dueCalc,
                    )
                }
            }

            is CreditCardFormAction.DueDayChanged -> {
                val closingCalc = action.dueDay.toIntOrNull()
                    ?.let { creditCardPeriod.calculateClosingDay(it) }
                form.update {
                    it?.copy(
                        dueDayUser = action.dueDay,
                        closingDayCalc = closingCalc,
                    )
                }
            }

            is CreditCardFormAction.IconSelected -> {
                form.update { it?.copy(iconKey = action.icon.key) }
            }

            is CreditCardFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {
        form.update { it?.copy(name = newName) }
        validation[CreditCardField.NAME] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_credit_card_name",
        ) {
            validation[CreditCardField.NAME] = validateCreditCardName(
                name = newName,
                ignoreId = creditCardId,
            ).map {
                Validation.Valid
            }.getOrElse { error ->
                Validation.Error(error.toUiText())
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        val current = form.value ?: return@launch

        if (creditCardId != null) {
            current.build(id = creditCardId)
                .flatMap { creditCard ->
                    updateCreditCardUseCase(creditCard.id) {
                        it.copy(
                            name = creditCard.name,
                            limit = creditCard.limit,
                            closingDay = creditCard.closingDay,
                            dueDay = creditCard.dueDay,
                            iconKey = creditCard.iconKey,
                        )
                    }
                }.onLeft {
                    crashlytics.recordException(it)
                }.onRight {
                    analytics.logEvent(EditCreditCard)
                    modalManager.dismissAll()
                }

            return@launch
        }

        addCreditCardUseCase(
            form = current,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(CreateCreditCard)
            modalManager.dismiss()
        }
    }
}
