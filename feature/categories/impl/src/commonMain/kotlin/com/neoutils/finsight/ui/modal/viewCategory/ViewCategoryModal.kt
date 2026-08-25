@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.ui.util.optionalTestTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.CategoryOverview
import com.neoutils.finsight.domain.model.SpendingVariation
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.toPercentageString
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.ConsolidationBadge
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.DetailPaneController
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.modal.archiveCategory.ArchiveCategoryModal
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryModal
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_category_above_average
import com.neoutils.finsight.resources.view_category_at_average
import com.neoutils.finsight.resources.view_category_below_average
import com.neoutils.finsight.resources.view_category_edit
import com.neoutils.finsight.resources.view_category_history_range
import com.neoutils.finsight.resources.view_category_month_average
import com.neoutils.finsight.resources.view_category_partial_month
import com.neoutils.finsight.resources.view_category_see_transactions
import com.neoutils.finsight.resources.view_category_this_month
import com.neoutils.finsight.resources.view_category_total_received
import com.neoutils.finsight.resources.view_category_total_spent
import com.neoutils.finsight.resources.view_category_type_expense
import com.neoutils.finsight.resources.view_category_type_income
import com.neoutils.finsight.resources.view_category_unarchive
import com.neoutils.finsight.resources.view_category_window_total
import kotlin.math.abs
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class ViewCategoryModal(
    private val categoryId: Long,
) : AdaptiveModal() {

    // Both slots (RenderBody/RenderActions) render under this modal as their
    // ViewModelStoreOwner, so this resolves the same ViewModel and collects the same
    // state — the resolution lives here once, not copied into each slot, and the
    // collector is lifecycle-aware because the state reacts to observeLedgerChanges()
    // (design D8).
    @Composable
    private fun rememberViewState(): Pair<ViewCategoryViewModel, ViewCategoryUiState> {
        val viewModel = koinViewModel<ViewCategoryViewModel> { parametersOf(categoryId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        return viewModel to uiState
    }

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val (viewModel, uiState) = rememberViewState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewCategoryEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewCategoryUiState.Loading -> DetailLoadingState()
            ViewCategoryUiState.Error -> DetailErrorState()
            is ViewCategoryUiState.Content -> ContentBody(
                uiState = state,
                detailController = detailController,
            )
        }
    }

    @Composable
    private fun ContentBody(
        uiState: ViewCategoryUiState.Content,
        detailController: DetailPaneController,
    ) {
        val navController = LocalNavController.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {

            Header(uiState = uiState, onSeeRates = { navController.navigate(ExchangeRatesRoute) })

            // An absence is shown by absence. A category nothing was posted to states no
            // figure and says nothing about there being none — and it offers no command
            // either, because the list it leads to would come up empty. That is why the
            // command lives *inside* the figures block: it cannot outlive them.
            val openTransactions = {
                // Dismissed first, as every detail that leaves for another screen does:
                // the sheet is not part of where the command leads.
                detailController.dismiss()
                navController.navigate(TransactionsRoute(filterCategoryId = uiState.category.id))
            }

            when (val overview = uiState.overview) {
                CategoryOverview.Empty -> Unit

                is CategoryOverview.Active -> Figures(openTransactions) {
                    ActiveBody(uiState = uiState, overview = overview)
                }

                is CategoryOverview.Archived -> Figures(openTransactions) {
                    ArchivedBody(uiState = uiState, overview = overview)
                }
            }
        }
    }

    /**
     * The figures, and under them the command that replaces the period control this
     * surface no longer has: "how much did I spend on this in March" stays answerable, on
     * the screen that already answers it, and above the fold before the body scrolls.
     */
    @Composable
    private fun Figures(
        onOpenTransactions: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        content()

        Spacer(modifier = Modifier.height(20.dp))

        FilledTonalButton(
            onClick = onOpenTransactions,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("view_category_transactions"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.view_category_see_transactions),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    @Composable
    private fun Header(uiState: ViewCategoryUiState.Content, onSeeRates: () -> Unit) {
        val isIncome = uiState.category.type.isIncome
        val typeLabel = stringResource(
            if (isIncome) Res.string.view_category_type_income else Res.string.view_category_type_expense
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // The proportions the transaction detail uses, not the 64dp the budget and
            // recurring sheets still carry: at that size the icon competes with the name
            // beside it, on a sheet whose subject is the figures below.
            CategoryIconBox(
                category = uiState.category,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(13.dp),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = uiState.category.displayColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = uiState.category.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface
                )
            }

            // A category is a dimension, not an account: its entries may sit in several
            // currencies, so every figure here is a consolidated one (design D13). The
            // badge answers for all of them at once — it is one mark about one surface.
            ConsolidationBadge(
                figures = uiState.overview.figures,
                onSeeRates = onSeeRates,
            )
        }
    }

    /**
     * A live category: the current month in the highlight, announced as unfinished, and
     * read against the window below it.
     */
    @Composable
    private fun ActiveBody(
        uiState: ViewCategoryUiState.Content,
        overview: CategoryOverview.Active,
    ) {
        FigureRow(
            label = stringResource(Res.string.view_category_this_month),
            amount = overview.currentMonth.amount,
            caption = stringResource(
                Res.string.view_category_partial_month,
                overview.currentMonth.elapsedDay,
                overview.currentMonth.daysInMonth,
            ),
            valueColor = uiState.category.displayColor,
            valueTestTag = "view_category_month_amount",
        )

        Variation(overview.variation)

        overview.window?.let { window ->
            Spacer(modifier = Modifier.height(16.dp))

            FigureRow(
                label = pluralStringResource(
                    Res.plurals.view_category_month_average,
                    window.months,
                    window.months,
                ),
                amount = window.average,
            )

            Spacer(modifier = Modifier.height(8.dp))

            FigureRow(
                label = pluralStringResource(
                    Res.plurals.view_category_window_total,
                    window.months,
                    window.months,
                ),
                amount = window.total,
                valueTestTag = TOTAL_TEST_TAG,
            )
        }
    }

    /**
     * An archived category: the current month says nothing about it, so the whole history
     * takes the highlight, over the range it covers.
     */
    @Composable
    private fun ArchivedBody(
        uiState: ViewCategoryUiState.Content,
        overview: CategoryOverview.Archived,
    ) {
        val formats = LocalDateFormats.current
        FigureRow(
            label = stringResource(
                if (uiState.category.type.isIncome) Res.string.view_category_total_received
                else Res.string.view_category_total_spent
            ),
            amount = overview.total,
            caption = stringResource(
                Res.string.view_category_history_range,
                formats.yearMonth.format(overview.firstMonth),
                formats.yearMonth.format(overview.lastMonth),
            ),
            valueColor = uiState.category.displayColor,
            valueTestTag = TOTAL_TEST_TAG,
        )
    }

    /**
     * How the month stands against the average: words, an arrow, and a colour — the three
     * agreeing, so the reading survives whichever of them a person actually takes in.
     *
     * The colour is **never** the app's income or expense colour. Those two already mean
     * one thing each on every other surface, and painting "spent less" green on an expense
     * category would make the same green say two things on one screen. What is used
     * instead is the severity pair — amber for the month that runs above its own average,
     * blue for the one that runs below — which says "pay attention here" without claiming
     * a nature the figure does not have. The text alone still carries the direction, so
     * nothing is lost where the colour is not seen.
     *
     * When there is no answer, this renders nothing at all: an absence is shown by
     * absence, not by a sentence about it.
     */
    @Composable
    private fun Variation(variation: SpendingVariation) {
        if (variation !is SpendingVariation.Measured) return

        // The gap belongs to this line and travels with it. Left outside, it stayed
        // behind when the line did not render, and the sheet showed a band of nothing
        // where the absence was supposed to be invisible.
        Spacer(modifier = Modifier.height(12.dp))

        val tint = when {
            variation.isAtAverage -> colorScheme.onSurfaceVariant
            variation.isAbove -> Warning
            else -> Info
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!variation.isAtAverage) {
                Icon(
                    imageVector = if (variation.isAbove) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = variationText(variation),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.testTag("view_category_variation"),
            )
        }
    }

    @Composable
    private fun variationText(variation: SpendingVariation.Measured): String =
        if (variation.isAtAverage) {
            stringResource(Res.string.view_category_at_average)
        } else {
            stringResource(
                if (variation.isAbove) Res.string.view_category_above_average
                else Res.string.view_category_below_average,
                (abs(variation.fraction) * PERCENT).toPercentageString(),
            )
        }

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val (viewModel, uiState) = rememberViewState()
        val content = uiState as? ViewCategoryUiState.Content ?: return

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Retire and unarchive are mutually exclusive by archived state: a category
            // is only archived once it has entries, so the two offers never overlap. A
            // screen decides whether it offers an action, never which one it is.
            if (content.category.isArchived) {
                OutlinedActionButton(
                    label = stringResource(Res.string.view_category_unarchive),
                    icon = Icons.Default.Unarchive,
                    contentColor = colorScheme.primary,
                    onClick = { viewModel.onAction(ViewCategoryAction.Unarchive) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("category_unarchive"),
                )
            } else {
                OutlinedActionButton(
                    label = stringResource(content.retireAction.label),
                    icon = content.retireAction.icon,
                    contentColor = colorScheme.error,
                    onClick = {
                        manager.show(
                            when (content.retireAction) {
                                RetireAction.DELETE -> DeleteCategoryModal(content.category)
                                RetireAction.ARCHIVE -> ArchiveCategoryModal(content.category)
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("category_retire"),
                    // Which of the two words this button carries *is* the claim, so the
                    // assertion has to read the node that renders it.
                    labelTestTag = "category_retire_label",
                )
            }

            OutlinedActionButton(
                label = stringResource(Res.string.view_category_edit),
                icon = Icons.Default.Edit,
                contentColor = Info,
                onClick = { manager.show(CategoryFormModal(content.category)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    /**
     * A `label → figure` line, with the period the figure covers stated under the label.
     *
     * The caption is not decoration: a figure whose period is not said answers neither
     * "is that a lot?" nor "since when?".
     */
    @Composable
    private fun FigureRow(
        label: String,
        amount: ConsolidatedAmount,
        caption: String? = null,
        valueColor: Color = colorScheme.onSurface,
        valueTestTag: String? = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    // Medium rather than the style's own Normal: at `bodyLarge` this row
                    // read lighter than the same row on every other detail sheet, which
                    // sets its value in SemiBold. Both halves are lifted a step, together,
                    // so the three surfaces go on reading alike.
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
                caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            MoneyText(
                figure = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.optionalTestTag(valueTestTag),
            )
        }
    }

    private companion object {
        /**
         * The **total** figure of the detail, whichever total the state has to show: the
         * window's while the category is live, the whole history once it is archived. One
         * slot, never two at a time, so the name says what the surface is asserting.
         */
        const val TOTAL_TEST_TAG = "view_category_total_amount"

        const val PERCENT = 100.0
    }
}

/**
 * Every money figure this state puts on screen, for the one mark that answers for all of
 * them. It is derived here rather than listed at the call site so a figure added to a
 * variant cannot quietly escape the badge.
 */
private val CategoryOverview.figures: List<ConsolidatedAmount>
    get() = when (this) {
        CategoryOverview.Empty -> emptyList()
        is CategoryOverview.Active -> listOfNotNull(
            currentMonth.amount,
            window?.average,
            window?.total,
        )

        is CategoryOverview.Archived -> listOf(total)
    }
