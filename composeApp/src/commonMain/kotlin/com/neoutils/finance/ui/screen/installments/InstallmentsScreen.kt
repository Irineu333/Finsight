@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.neoutils.finance.ui.screen.installments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.OperationCard
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finance.util.DateFormats
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

private val formats = DateFormats()

@Composable
fun InstallmentsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: InstallmentsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InstallmentsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun InstallmentsContent(
    uiState: InstallmentsUiState,
    onAction: (InstallmentsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Parcelamentos")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.installments.isNotEmpty()) {
                item(key = "installments_pager") {
                    InstallmentPager(
                        installments = uiState.installments,
                        selectedIndex = uiState.selectedInstallmentIndex,
                        onSelectInstallment = { index ->
                            onAction(InstallmentsAction.SelectInstallment(index))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.selectedInstallment?.let { selectedInstallment ->
                    items(
                        items = selectedInstallment.operations,
                        key = Operation::id,
                    ) { operation ->
                        OperationCard(
                            operation = operation,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .animateItem(),
                            onClick = {
                                when (operation.type) {
                                    Transaction.Type.ADJUSTMENT -> {
                                        modalManager.show(ViewAdjustmentModal(operation))
                                    }

                                    else -> {
                                        modalManager.show(ViewOperationModal(operation))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentPager(
    installments: List<InstallmentWithOperationsUi>,
    selectedIndex: Int,
    onSelectInstallment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { installments.size },
    )

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onSelectInstallment(page)
            }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in installments.indices && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp,
    ) { page ->
        InstallmentSummaryCard(
            ui = installments[page],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InstallmentSummaryCard(
    ui: InstallmentWithOperationsUi,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${ui.installment.count} parcelas",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Valor total ${ui.installment.totalAmount.toMoneyFormat()}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Última parcela em ${formats.dayMonthYear.format(ui.latestOperationDate)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
