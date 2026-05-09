package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.extension.CurrencyFormatter
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.event.ConfirmRecurring
import com.neoutils.finsight.feature.recurring.event.SkipRecurring
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.recurring.model.form.RecurringConfirmForm
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.recurring.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.SkipRecurringUseCase
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ConfirmRecurringViewModel(
    private val recurringId: Long,
    private val targetDate: LocalDate,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val currencyFormatter: CurrencyFormatter,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun LocalDate.clampToToday() = takeIf { it <= currentDate } ?: currentDate

    private val form = MutableStateFlow<RecurringConfirmForm?>(null)

    private val invoices = form
        .map { it?.creditCard }
        .distinctUntilChanged()
        .map { creditCard ->
            creditCard?.let {
                invoiceRepository.getInvoicesByCreditCard(it.id)
            }.orEmpty()
        }

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        val resolved = recurringRepository.getRecurringById(recurringId)

        if (resolved == null) {
            crashlytics.recordException(RecurringException(RecurringError.NOT_FOUND))
            modalManager.dismiss()
            return@launch
        }

        form.value = RecurringConfirmForm(
            title = resolved.title,
            type = resolved.type,
            category = resolved.categoryId?.let {
                categoryRepository.getCategoryById(it)
            },
            date = targetDate.clampToToday(),
            amount = currencyFormatter.format(resolved.amount),
            target = if (resolved.creditCardId != null) {
                Transaction.Target.CREDIT_CARD
            } else {
                Transaction.Target.ACCOUNT
            },
            account = resolved.accountId?.let {
                accountRepository.getAccountById(it)
            },
            creditCard = resolved.creditCardId?.let {
                creditCardRepository.getCreditCardById(it)
            },
            invoice = resolved.creditCardId?.let {
                invoiceRepository.getOpenInvoice(it)
            },
        )
    }

    val uiState = combine(
        form.filterNotNull(),
        invoices,
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
    ) { form, invoices, accounts, creditCards ->
        ConfirmRecurringUiState.Content(
            form = form,
            accounts = accounts,
            creditCards = creditCards,
            invoices = invoices,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConfirmRecurringUiState.Loading,
    )

    fun onAction(action: ConfirmRecurringAction) {
        when (action) {
            is ConfirmRecurringAction.TargetSelected -> {
                changeTarget(action.target)
            }

            is ConfirmRecurringAction.AccountSelected -> {
                form.update { it?.copy(account = action.account) }
            }

            is ConfirmRecurringAction.CreditCardSelected -> {
                changeCreditCard(action.creditCard)
            }

            is ConfirmRecurringAction.DateChanged -> {
                form.update { it?.copy(date = action.date.clampToToday()) }
            }

            is ConfirmRecurringAction.InvoiceSelected -> {
                form.update { it?.copy(invoice = action.invoice) }
            }

            is ConfirmRecurringAction.AmountChanged -> {
                form.update { it?.copy(amount = action.amount) }
            }

            ConfirmRecurringAction.Confirm -> confirm()
            ConfirmRecurringAction.Skip -> skip()
        }
    }

    private fun changeCreditCard(creditCard: CreditCard) = viewModelScope.launch {
        val current = form.value ?: return@launch

        val invoice = current.invoice
            ?.takeIf { it.creditCardId == creditCard.id }
            ?: invoiceRepository.getOpenInvoice(creditCard.id)

        form.update { it?.copy(creditCard = creditCard, invoice = invoice) }
    }

    private fun changeTarget(target: Transaction.Target) = viewModelScope.launch {
        val current = form.value ?: return@launch

        val creditCard = current.creditCard ?: creditCardRepository.getAllCreditCards().firstOrNull()
        val account = current.account ?: accountRepository.getAllAccounts().firstOrNull()

        val invoice = current.invoice
            ?.takeIf { it.creditCardId == creditCard?.id }
            ?: creditCard?.let {
                invoiceRepository.getOpenInvoice(it.id)
            }

        form.update {
            it?.copy(
                target = target,
                creditCard = creditCard,
                account = account,
                invoice = invoice,
            )
        }
    }

    private fun confirm() = viewModelScope.launch {
        val form = form.value ?: return@launch

        confirmRecurringUseCase(
            recurringId = recurringId,
            date = form.date,
            amount = form.amount,
            target = form.target,
            account = form.account.takeIf { form.target.isAccount },
            creditCard = form.creditCard.takeIf { form.target.isCreditCard },
            invoice = form.invoice.takeIf { form.target.isCreditCard },
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(
                ConfirmRecurring(
                    type = form.type,
                    target = form.target,
                    categoryId = form.category?.id,
                )
            )
            modalManager.dismiss()
        }
    }

    private fun skip() = viewModelScope.launch {
        val form = form.value ?: return@launch

        skipRecurringUseCase(
            recurringId = recurringId,
            date = form.date,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(
                SkipRecurring(
                    type = form.type,
                    target = form.target,
                    categoryId = form.category?.id,
                )
            )
            modalManager.dismiss()
        }
    }
}
