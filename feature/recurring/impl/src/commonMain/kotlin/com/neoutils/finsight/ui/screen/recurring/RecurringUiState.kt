package com.neoutils.finsight.ui.screen.recurring

import androidx.compose.runtime.Immutable
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.operationName
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_screen_day
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonth
import kotlinx.datetime.YearMonth

/**
 * What a row of the list asserts — the four things that tell one cycle from the next:
 * what it **is**, where it **posts**, **how much**, and **when**.
 *
 * **The list has one row, and it is this one.** It is filled from either of two sources,
 * and it carries no trace of which: the choice belongs to [RecurringCycleUi], and the
 * component that draws the row does not know there are two. A second component for one of
 * the sections would stack two blocks of different screens in one list and leave its
 * height without an owner — two definitions that diverge at the first adjustment of
 * either side.
 *
 * [amount] is `null` for a figure that cannot be resolved at all: the row renders the
 * unresolved mark in its place and [source] states the cause. A row read from the ledger
 * never is — the money moved, and it moved in a currency.
 *
 * [category] is the chip's, and [direction] the mark beside the identity: both are read
 * from whichever source the row came from, so the same category is the same colour
 * wherever it appears in the list.
 *
 * The two natures the row draws are total over what it can be handed. A confirmation
 * always writes a nominal contra leg and never an `EQUITY` one (`contraLegFor`), so the
 * transaction of a cycle reads as expense or income and never as an adjustment — including
 * on a card, whose `LIABILITY` leg loses to the nominal one in `deriveTransactionLabel`.
 */
@Immutable
data class RecurringRowUi(
    val identity: UiText,
    val direction: TransactionType,
    val category: Category?,
    val amount: DisplayAmount?,
    val source: RecurringRowSource,
    val moment: UiText,
)

/**
 * Where the money moves through, as the row names it.
 *
 * [name] is `null` only when there is nothing left to call it — see [sourceName] for what
 * that means on a template.
 *
 * [isUsable] is false when the source can receive nothing new and the template cannot post
 * at all — the gravest thing this row states. It is a fact about a **rule**: a row read
 * from the ledger describes money that already moved, and nothing that happened to the
 * account afterwards makes that untrue.
 */
data class RecurringRowSource(
    val name: String?,
    val isCard: Boolean,
    val isUsable: Boolean = true,
)

/**
 * A cycle as the list renders it — in the two shapes a cycle can be **read** in.
 *
 * The split is not decoration: a cycle with nothing recorded for it can only be described
 * by the template that projects it, and a cycle that was posted is described by the
 * ledger. Which of the two a row is comes from the section it is in, so no row has to say
 * it for itself.
 *
 * Two sources, and one [row]: what varies is where the figure, the identity, the
 * classification and the source are read from, never how they are drawn.
 */
sealed class RecurringCycleUi {

    /** The template the cycle belongs to — what every row leads to when tapped. */
    abstract val recurring: Recurring

    /** What the list draws, with the source it was read from already resolved away. */
    abstract val row: RecurringRowUi

    /**
     * A cycle read from the **template**: pending, upcoming, or skipped. None of the
     * three has a fact to read, and the figure is what the template says the month
     * would ask for.
     *
     * [amount] is `null` when no account denominates the template — its source was
     * deleted, so there is no currency the value could be read in. The row does not
     * drop the figure: it renders the unresolved mark in its place and states the cause
     * beside it, because in a dense list an absence is invisible and a row that changes
     * height explains nothing.
     */
    data class Template(
        override val recurring: Recurring,
        val amount: DisplayAmount?,
    ) : RecurringCycleUi() {
        override val row = templateRowOf(recurring, amount)
    }

    /**
     * A cycle read from the **ledger**: what the transaction of that cycle registered —
     * its figure, its identity, its classification and the [source] it posted to — and
     * not what the template predicted.
     *
     * Confirming a cycle may override every one of those for that month alone, so a row
     * that read the template would assert about the month a number and a name that may
     * never have existed.
     *
     * [source] in particular cannot be borrowed from the template: the account and the
     * card are overridable per cycle (`ConfirmRecurringUseCase`) and the occurrence keeps
     * no record of the choice, so the true source of that month lives only in the legs of
     * the transaction. [category] comes with it rather than off `TransactionUi`, which is
     * a flat DTO and carries no facade: the chip reads the category's own colour, and it
     * must be the same colour here as on the template row beside it.
     */
    data class Posted(
        override val recurring: Recurring,
        val transaction: TransactionUi,
        val category: Category?,
        val source: RecurringRowSource,
    ) : RecurringCycleUi() {
        override val row = RecurringRowUi(
            identity = operationName(
                displayTitle = transaction.title,
                label = transaction.label,
                isCardTarget = transaction.isCardTarget,
            ),
            direction = transaction.direction,
            category = category,
            amount = transaction.amount,
            source = source,
            // Where the template asserts the day it projects, the fact asserts the date it
            // was registered on: a cycle confirmed off its day says so on its own row. The
            // year is left out because the whole screen is already one month.
            moment = UiText.Raw(dayMonth.format(transaction.date)),
        )
    }
}

/**
 * The row of a **template**: what the rule itself says, in the four slots the row has.
 *
 * It is the reading of every cycle with no fact behind it, and the only one an archived
 * template ever has — the archive lists rules that generate no cycle at all. One owner, so
 * the two destinations that list the same object cannot drift into two vocabularies for
 * it.
 *
 * [amount] is `null` when nothing denominates the template: the row renders the unresolved
 * mark in its place, and the source line beside it states the cause.
 */
internal fun templateRowOf(recurring: Recurring, amount: DisplayAmount?) = RecurringRowUi(
    identity = UiText.Raw(recurring.label),
    direction = recurring.type,
    category = recurring.category,
    amount = amount,
    source = RecurringRowSource(
        name = recurring.sourceName(),
        isCard = recurring.creditCard != null,
        isUsable = recurring.hasUsableSource,
    ),
    // What the template projects: the day of the month it falls on.
    moment = UiText.ResWithArgs(Res.string.recurring_screen_day, recurring.dayOfMonth),
)

/**
 * What the row calls the template's source — `null` only when there is nothing left to
 * call it.
 *
 * **Archived and removed are two absences, and the row keeps them apart.** Both make
 * `Recurring.hasUsableSource` false, but reading them as one would leave the unusable
 * branch without a name, and two "Aluguel" in two archived banks would read identically —
 * losing precisely the distinction the row exists to make. A removed source is `null` on
 * both sides (the foreign key is
 * `SET_NULL`) and genuinely has no name; an archived one exists, is named, and archiving is
 * offered to the user as reversible — it may take away the path to the account, never the
 * account's name.
 *
 * The card comes first, as everywhere else that resolves a template's source: it is the
 * more specific of the two, and a template that names one is denominated by it.
 */
internal fun Recurring.sourceName(): String? = creditCard?.name ?: account?.name

/**
 * One state of cycle, and the cycles in it.
 *
 * A section only exists when it has cycles: an empty one would be the month summary
 * asserting an absence it already asserts, with less precision.
 */
data class RecurringSection(
    val status: RecurringCycleStatus,
    val cycles: List<RecurringCycleUi>,
)

/**
 * The month above the list, in the four figures the screen shows.
 *
 * Fact and forecast are two classes of thing and are labelled as such: money in the
 * ledger, and money the month may still ask for. Every figure arrives consolidated —
 * they span accounts, so they can span currencies — and each carries its own sign policy,
 * which here is a magnitude in all four: the card shows no total, so there is no sum for
 * a sign to be the effect on.
 *
 * [undenominated] is the one count the card still carries, and it is not a count of
 * cycles: no section of the list accounts for it, and it speaks of a failure — a template
 * pointing at an account that no longer exists — whose way out is pointing it somewhere
 * else.
 */
data class RecurringMonthSummary(
    val settledExpense: ConsolidatedAmount,
    val settledIncome: ConsolidatedAmount,
    val forecastExpense: ConsolidatedAmount,
    val forecastIncome: ConsolidatedAmount,
    val undenominated: Int,
) {
    /** Every figure the card draws — what the badge decides its own level from. */
    val figures: List<ConsolidatedAmount>
        get() = listOf(settledExpense, settledIncome, forecastExpense, forecastIncome)
}

sealed class RecurringUiState {

    abstract val filter: RecurringFilter

    data class Loading(
        override val filter: RecurringFilter = RecurringFilter.ALL,
    ) : RecurringUiState()

    /**
     * The database holds no recurring at all — the only case that earns the big CTA
     * empty-state. A month that merely happens to have no cycle is [Content] with no
     * section. [filter] survives so the FAB is still shown and knows its context.
     *
     * No summary here, and none is offered: with no template at all there is no month to
     * summarise, and the screen's whole job is the offer to create the first one.
     */
    data class Empty(
        override val filter: RecurringFilter,
    ) : RecurringUiState()

    /**
     * [selectedYearMonth] governs both halves of the screen. What [sections] lists are
     * the **cycles** of the month, and a cycle has a month by definition — the summary
     * and the list answer the same question about the same month, and a selector that
     * moved only one of them would be indistinguishable from a defect.
     *
     * [filter] governs [sections] and nothing else: under a cut by nature the summary
     * would have to suppress one of the two lines of each block, changing *shape* while
     * the list changes *content*.
     *
     * [sections] arrives ordered — pending, upcoming, posted, skipped — and holds no
     * empty section.
     */
    data class Content(
        val sections: List<RecurringSection>,
        override val filter: RecurringFilter,
        val selectedYearMonth: YearMonth,
        val summary: RecurringMonthSummary,
    ) : RecurringUiState()
}
