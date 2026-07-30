package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.LAST_RESORT_CURRENCY
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_screen_day
import com.neoutils.finsight.resources.recurring_status_active
import com.neoutils.finsight.resources.recurring_status_archived
import com.neoutils.finsight.resources.view_recurring_account_label
import com.neoutils.finsight.resources.view_recurring_amount_label
import com.neoutils.finsight.resources.view_recurring_category_label
import com.neoutils.finsight.resources.view_recurring_credit_card_label
import com.neoutils.finsight.resources.view_recurring_day_label
import com.neoutils.finsight.resources.view_recurring_edit
import com.neoutils.finsight.resources.view_recurring_status_label
import com.neoutils.finsight.resources.view_recurring_type_label
import com.neoutils.finsight.resources.view_recurring_unarchive
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.component.DetailPaneController
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.modal.archiveRecurring.ArchiveRecurringModal
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringModal
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.unarchiveRecurring.UnarchiveRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Warning
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewRecurringModal(
    private val recurringId: Long,
) : AdaptiveModal() {

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val navController = LocalNavController.current
        val formatter = LocalCurrencyFormatter.current

        val viewModel = koinViewModel<ViewRecurringViewModel> { parametersOf(recurringId) }
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewRecurringEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewRecurringUiState.Loading -> DetailLoadingState()
            ViewRecurringUiState.Error -> DetailErrorState()
            is ViewRecurringUiState.Content -> ContentBody(
                recurring = state.recurring,
                formatter = formatter,
                detailController = detailController,
                navController = navController,
            )
        }
    }

    @Composable
    private fun ContentBody(
        recurring: Recurring,
        formatter: CurrencyFormatter,
        detailController: DetailPaneController,
        navController: NavController,
    ) {
        val typeColor = if (recurring.type.isIncome) Income else Expense

        val typeLabel = if (recurring.type.isIncome) {
            stringResource(Res.string.recurring_income)
        } else {
            stringResource(Res.string.recurring_expense)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val category = recurring.category
                if (category != null) {
                    CategoryIconBox(
                        category = category,
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                } else {
                    Surface(
                        color = typeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (recurring.type.isIncome) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Text(
                    text = recurring.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                DetailRow(
                    label = stringResource(Res.string.view_recurring_type_label),
                    value = typeLabel,
                    valueColor = typeColor,
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_amount_label),
                    value = formatter.format(recurring.amount, recurring.currency ?: LAST_RESORT_CURRENCY),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_day_label),
                    value = stringResource(Res.string.recurring_screen_day, recurring.dayOfMonth),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_status_label),
                    value = if (recurring.isArchived) {
                        stringResource(Res.string.recurring_status_archived)
                    } else {
                        stringResource(Res.string.recurring_status_active)
                    },
                    valueColor = if (recurring.isArchived) Warning else Income,
                    // Colour is never the only differentiator: the archived state also
                    // carries its own word and its own icon.
                    valueIcon = if (recurring.isArchived) Icons.Default.Archive else null,
                )
                recurring.account?.let { account ->
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_account_label),
                        value = account.name,
                        // A closed account keeps its name in history but is gone from
                        // the accounts screen, so there is nowhere to send the user.
                        onClick = if (account.isArchived) null else {
                            {
                                detailController.dismiss()
                                navController.navigate(AccountsRoute(account.id))
                            }
                        }
                    )
                }
                recurring.creditCard?.let { creditCard ->
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_credit_card_label),
                        value = creditCard.name,
                        // Same as an account: the closed card still resolves, so its
                        // name stays; only the shortcut goes.
                        onClick = if (creditCard.isArchived) null else {
                            {
                                detailController.dismiss()
                                navController.navigate(
                                    CreditCardsRoute(creditCard.id)
                                )
                            }
                        }
                    )
                }
                recurring.category?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_category_label),
                        value = it.name,
                    )
                }
            }
        }
    }

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val viewModel = koinViewModel<ViewRecurringViewModel> { parametersOf(recurringId) }
        val uiState by viewModel.uiState.collectAsState()

        val content = uiState as? ViewRecurringUiState.Content ?: return

        Actions(content = content, manager = manager, viewModel = viewModel)
    }

    @Composable
    private fun Actions(
        content: ViewRecurringUiState.Content,
        manager: ModalManager,
        viewModel: ViewRecurringViewModel,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Retire and unarchive are mutually exclusive by archived state. A screen
            // decides whether it offers an action, never which one it is — which is
            // why the retire offer comes resolved in the state.
            if (content.recurring.isArchived) {
                OutlinedActionButton(
                    label = stringResource(Res.string.view_recurring_unarchive),
                    icon = Icons.Default.Unarchive,
                    contentColor = colorScheme.primary,
                    onClick = { manager.show(UnarchiveRecurringModal(content.recurring)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                OutlinedActionButton(
                    label = stringResource(content.retireAction.label),
                    icon = content.retireAction.icon,
                    contentColor = colorScheme.error,
                    onClick = {
                        manager.show(
                            when (content.retireAction) {
                                RetireAction.DELETE -> DeleteRecurringModal(content.recurring)
                                RetireAction.ARCHIVE -> ArchiveRecurringModal(content.recurring)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedActionButton(
                label = stringResource(Res.string.view_recurring_edit),
                icon = Icons.Default.Edit,
                contentColor = Info,
                onClick = { manager.show(RecurringFormModal(content.recurring)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface,
        valueIcon: ImageVector? = null,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ) {
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (valueIcon != null) {
                    Icon(
                        imageVector = valueIcon,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                )
            }
        }
    }
}
