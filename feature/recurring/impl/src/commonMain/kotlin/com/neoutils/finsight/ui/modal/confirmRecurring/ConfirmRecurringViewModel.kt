package com.neoutils.finsight.ui.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ConfirmRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.skipRecurring.SkipRecurringModal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ConfirmRecurringViewModel(
    val recurring: Recurring,
    private val targetDate: LocalDate,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    // The clock the app was given, not the system's. What "today" means decides the month the
    // occurrence is filed under, so a screen reading a different clock from the rest of the app
    // would confirm a cycle into a month the app is not in.
    private val currentDate get() = clock.today()

    // Only an *open* account or card can be pre-selected. An archived one is still
    // shown on the recurring itself (that is its history), but the ledger refuses
    // to post to it, so here it leaves the selection empty and the user picks.
    private val initialAccount = recurring.account?.takeIf { !it.isArchived }
    private val initialCreditCard = recurring.creditCard?.takeIf { !it.isArchived }
    private val initialTarget = if (initialCreditCard != null) {
        TransactionTarget.CREDIT_CARD
    } else {
        TransactionTarget.ACCOUNT
    }
    /**
     * What the recurring's own amount is denominated in — the currency of the account or
     * card it names (design D17). `null` when it names neither, in which case there is
     * nothing to constrain and every account is offered.
     */
    private val recurringCurrency = recurring.account?.currency ?: recurring.creditCard?.currency

    private val confirmDate = MutableStateFlow(targetDate.takeIf { it <= currentDate } ?: currentDate)
    private val selectedTarget = MutableStateFlow(initialTarget)
    private val selectedAccount = MutableStateFlow(initialAccount)
    private val selectedCreditCard = MutableStateFlow(initialCreditCard)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    // Seeded from the template and free to differ: how this cycle is classified is a
    // fact about this month, and confirming it never writes back to the recurring.
    private val selectedCategory = MutableStateFlow(recurring.category)

    init {
        viewModelScope.launch {
            selectedCreditCard.collectLatest { creditCard ->
                if (creditCard == null) {
                    invoices.value = emptyList()
                    selectedInvoice.value = null
                    return@collectLatest
                }

                val allInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)
                invoices.value = allInvoices
                selectedInvoice.value = allInvoices.firstOrNull { it.status.isOpen } ?: allInvoices.firstOrNull()
            }
        }
    }

    /**
     * The selected card's currency, read off the `LIABILITY` account it projects onto
     * (design D17). Resolved beside the card so the two cannot disagree.
     */
    private val creditCardCurrency = selectedCreditCard.map { card ->
        card?.let { accountRepository.currencyOf(it) }
    }

    /**
     * What the category selector offers and what it shows selected, resolved together
     * because the offered list has to account for the selection (see [offeredCategories]).
     */
    private val categorySelection = combine(
        categoryRepository.observeAllCategories(),
        selectedCategory,
    ) { open, selected ->
        CategorySelection(
            offered = offeredCategories(open, recurring.type, selected),
            selected = selected,
        )
    }

    val uiState = combine(
        confirmDate,
        selectedTarget,
        selectedAccount,
        selectedCreditCard,
        selectedInvoice,
        invoices,
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        creditCardCurrency,
        categorySelection,
    ) { date, target, account, creditCard, invoice, invoiceList, accounts, creditCards, cardCurrency, category ->
        // No fallback to the default account: substituting where the money moves
        // through is not a detail the app gets to decide in silence. With nothing
        // selected the modal keeps Confirm disabled until the user says where.
        val offeredAccounts = accounts.offeredFor(recurringCurrency) { it.currency }
        val offeredCreditCards = creditCards.offeredFor(recurringCurrency) { it.currency }

        ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = date,
            categories = category.offered,
            selectedCategory = category.selected,
            selectedTarget = target,
            accounts = offeredAccounts,
            // A silently shorter list is a lie by omission, so the modal says why —
            // for either list, since both shrink for the same reason.
            hiddenByCurrency = offeredAccounts.size < accounts.size ||
                offeredCreditCards.size < creditCards.size,
            recurringCurrency = recurringCurrency,
            selectedAccount = account,
            creditCards = offeredCreditCards,
            selectedCreditCard = creditCard,
            invoices = invoiceList,
            selectedInvoice = invoice,
            creditCardCurrency = cardCurrency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = targetDate.takeIf { it <= currentDate } ?: currentDate,
            selectedTarget = initialTarget,
            selectedAccount = initialAccount,
            selectedCreditCard = initialCreditCard,
            selectedCategory = recurring.category,
            categories = listOfNotNull(recurring.category),
        ),
    )

    fun onAction(action: ConfirmRecurringAction) {
        when (action) {
            is ConfirmRecurringAction.TargetSelected -> {
                selectedTarget.value = action.target
                if (action.target.isCreditCard && selectedCreditCard.value == null) {
                    selectedCreditCard.value = uiState.value.creditCards.firstOrNull()
                }
            }

            is ConfirmRecurringAction.AccountSelected -> selectedAccount.value = action.account
            is ConfirmRecurringAction.CreditCardSelected -> selectedCreditCard.value = action.creditCard
            is ConfirmRecurringAction.DateChanged -> {
                confirmDate.value = action.date.takeIf { it <= currentDate } ?: currentDate
            }

            is ConfirmRecurringAction.InvoiceSelected -> selectedInvoice.value = action.invoice
            is ConfirmRecurringAction.CategorySelected -> selectedCategory.value = action.category
            is ConfirmRecurringAction.Confirm -> confirm(action.amount, action.title)
            is ConfirmRecurringAction.Skip -> askToSkip()
        }
    }

    private fun confirm(amount: String, title: String) = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate

        val parsedAmount = amount.filter { it.isDigit() }
            .toLongOrNull()
            ?.toDouble()
            ?.div(100)
            ?: recurring.amount

        confirmRecurringUseCase(
            recurring = recurring,
            date = date,
            amount = parsedAmount,
            target = uiState.value.selectedTarget,
            account = if (uiState.value.selectedTarget.isAccount) uiState.value.selectedAccount else null,
            creditCard = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedCreditCard else null,
            invoice = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedInvoice else null,
            // Blank is an absence, not the template's title: a transaction with no title
            // of its own is displayed by its category, which is the rule the whole app
            // reads titles by (`displayTitleOrNull`). Falling back to the template here would
            // hand the user a name they had just erased.
            title = title.trim().takeIf { it.isNotBlank() },
            category = selectedCategory.value,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(UiText.Res(Res.string.retire_action_error_generic))
        }.onRight {
            analytics.logEvent(ConfirmRecurring(recurring, uiState.value.selectedTarget))
            modalManager.dismiss()
        }
    }

    /**
     * Asks before skipping, instead of skipping.
     *
     * The sheet it opens carries the date this confirmation is standing on: the month
     * the occurrence is filed under is the content of the decision, and re-deriving it
     * from the clock there would file a different one.
     */
    private fun askToSkip() {
        modalManager.show(
            SkipRecurringModal(
                recurring = recurring,
                date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate,
                target = uiState.value.selectedTarget,
            )
        )
    }
}

/** What the category selector offers, and which of those is chosen. */
private data class CategorySelection(
    val offered: List<Category>,
    val selected: Category?,
)

/**
 * The categories a confirmation may be classified under.
 *
 * Two rules, both borrowed rather than invented here. Coherence between the transaction
 * type and the category's own is `isAccept`'s, the same function the recurring form
 * consumes — a selector decides *whether* it filters, never *which* rule it filters by.
 * And continuity of a facade already chosen: [open] carries no archived category, so a
 * category archived *after* the template elected it would vanish from the selector and
 * silently unclassify the cycle. It is added back because it is already chosen, never
 * offered fresh — dropped once, it is gone while it stays archived.
 */
internal fun offeredCategories(
    open: List<Category>,
    type: TransactionType,
    selected: Category?,
): List<Category> {
    val offered = open.filter { it.type.isAccept(type) }
    return when {
        selected == null -> offered
        offered.any { it.id == selected.id } -> offered
        else -> offered + selected
    }
}

/**
 * The destinations a confirmation may be pointed at, given what the recurring's amount is
 * denominated in (design D17).
 *
 * A selector offers only what the domain accepts — D5's doctrine applied to the UI, and
 * what `TransferBetweenAccountsModal` already practises by leaving the source out of the
 * destinations. Redirecting a confirmation to an account or a card of another currency
 * would write the raw number as if it were that currency, so the refusal is prevented in
 * the control instead of reported as an error; the domain guard stays as a net, and is
 * never the designed path.
 *
 * **Accounts and cards go through the same function because they are the same rule.** They
 * had two copies of it and the card's copy was missing, which is the shape this kind of
 * omission takes: the domain refuses a card of another currency exactly as it refuses an
 * account, so an unfiltered card list offers what would be rejected. It matters more for
 * cards, in fact — choosing the card target auto-selects the first one, so the app can
 * land on the wrong currency without the user touching the selector at all.
 *
 * A recurring that names neither account nor card has nothing to constrain, and everything
 * is offered.
 */
internal fun <T> List<T>.offeredFor(currency: String?, currencyOf: (T) -> String?): List<T> =
    if (currency == null) this else filter { currencyOf(it) == currency }
