package com.neoutils.finsight.ui.modal.archiveCreditCard

import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ArchiveCreditCard
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.extension.requireCurrencyOf
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ArchiveCreditCardViewModel(
    private val creditCard: CreditCard,
    private val archiveCreditCardUseCase: ArchiveCreditCardUseCase,
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /**
     * The card's outstanding debt — see `ArchiveAccountViewModel`. `OWED` because a
     * card balance is stored negative and the sentence the user reads is "you still
     * owe this much", and denominated by the card itself (design D17).
     */
    val debt = MutableStateFlow<DisplayAmount?>(null)

    /**
     * Whether the card still carries **any** balance, in either direction — which is
     * what the use case refuses on, and not the same question as [debt]: a card in
     * credit owes nothing and still cannot be retired. Null until it is known.
     */
    val hasBalance = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            val balance = entryRepository.balance(creditCard.accountId)
            hasBalance.value = balance != 0.0
            debt.value = DisplayAmount.owed(
                value = balance,
                currency = accountRepository.requireCurrencyOf(creditCard),
                isApproximate = false,
            )
        }
    }



    fun archiveCreditCard() = viewModelScope.launch {
        archiveCreditCardUseCase(creditCard).onRight {
            analytics.logEvent(ArchiveCreditCard)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }
    }

    /**
     * A refused action has a reason the user can act on — "this account still has a
     * balance", "this category has transactions". Without this the sheet just did
     * not close and said nothing.
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is AccountException -> error.toUiText()
        else -> UiText.Res(Res.string.retire_action_error_generic)
    }
}
