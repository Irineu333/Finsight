@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.ui.model.RetireAction

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.util.UiText
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_payment_pay
import org.jetbrains.compose.resources.StringResource

data class InvoiceTransactionsUiState(
    val creditCardName: String = "",
    // The card's ledger account — this screen's perspective. A transaction here is read
    // through the card's own leg, so an invoice payment reads as money coming in.
    val cardAccountId: Long? = null,
    // An archived card is read-only history: this screen still shows its invoices and
    // transactions, but offers no write action (close/pay/advance/adjust). Deciding
    // whether to offer the action is the screen's job; the ledger already refuses the
    // ones that would write, and closing an invoice — which does not — is refused here.
    val isArchived: Boolean = false,
    // Which retire action this screen may offer for the card — the same rule the
    // cards screen uses, so the two cannot drift.
    val retireAction: RetireAction = RetireAction.DELETE,
    val invoices: List<InvoiceSummary> = emptyList(),
    val selectedInvoiceIndex: Int = 0,
    val listState: ListState = ListState.Loading,
    val categories: List<Category> = emptyList(),
    val selectedSubject: SpendingSubject? = null,
        /**
     * Whether anything the other controls left standing is unclassified. The value is
     * offered only then — a command that could answer nothing but an empty list is not
     * an offer — and stays while selected, so the cut is always undoable.
     */
    val hasUncategorized: Boolean = false,
    val selectedType: TransactionType? = null,
    val showRecurringOnly: Boolean = false,
    val showInstallmentOnly: Boolean = false,
) {

    /** Whether the menu offers the unclassified value (see [hasUncategorized]). */
    val mustShowUncategorizedFilter =
        hasUncategorized || selectedSubject == SpendingSubject.Uncategorized


    /**
     * What stands where the list goes. The transactions live *inside* [ListState.Content]
     * rather than beside it, which is what makes the ambiguity impossible: this screen's
     * default state used to be an empty map, indistinguishable from an invoice legitimately
     * without transactions, so the blank flashed on every load — including the ones that
     * had something to show.
     *
     * [Loading] is a starting state, not a recurring one: switching invoice or filter cuts
     * over data already observed and never returns here.
     *
     * The chrome above the list — the invoice pager, its actions and the chips — is not
     * part of this: it survives every state.
     */
    sealed interface ListState {

        /** No read has landed yet. The screen asserts nothing — not even emptiness. */
        data object Loading : ListState

        /** The selected invoice has no transaction at all. */
        data object EmptyInvoice : ListState

        /**
         * The invoice has transactions; none survives the active filters.
         * [canClearFilters] is false when every filter is already neutral.
         */
        data class EmptyScope(val canClearFilters: Boolean) : ListState

        data class Content(
            val transactions: Map<LocalDate, List<TransactionUi>>,
        ) : ListState
    }

    /**
     * [total] is `NATURAL`, **not** `OWED`, and that is not an oversight: it comes from
     * `owedByDimension`, which already returns debt-as-positive — the inversion happened
     * upstream, and `OWED` (`max(0, −value)`) would zero it. It also feeds
     * `currentBillAmount` on the payment and advance-payment modals, which is form
     * pre-filling rather than text: under `OWED` the payment modal would open at zero.
     */
    data class InvoiceSummary(
        val invoice: Invoice,
        val expense: DisplayAmount,
        val advancePayment: DisplayAmount,
        val adjustment: DisplayAmount,
        val total: DisplayAmount,
        val dueMonth: YearMonth,
        val nextDateLabel: UiText?,
        val closingDate: LocalDate,
        val isClosable: Boolean,
        val canReopen: Boolean = false,
        /**
         * How many transactions are posted to this invoice, before any filter.
         *
         * It exists for one sentence: the deletion confirmation states what the invoice
         * takes with it, and the transactions it takes are the invoice's own, not the ones
         * the chips happen to be leaving standing. Reading it from
         * [ListState.Content.transactions] would count the cut instead.
         */
        val transactionCount: Int = 0,
        /**
         * Whether this invoice has a payment to offer, and the verb that names it —
         * resolved from the domain's predicate, beside [isClosable] and [canReopen], so
         * the screen reads a fact rather than re-deciding a rule from the status.
         */
        val canPay: Boolean = false,
        val payLabel: StringResource = Res.string.invoice_payment_pay,
        /**
         * Whether paying discharges the invoice. A discharge is the action this screen
         * recommends and gives its solid emphasis to; a part-payment stays outlined.
         */
        val paySettles: Boolean = false,
    ) {
        val invoiceId = invoice.id
        val status = invoice.status
        val mustShowAdjustment = adjustment.value != 0.0
        val canEdit = status.isEditable
    }
}
