package com.neoutils.finsight.ui.screen.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.mapper.InstallmentUiMapper
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import com.neoutils.finsight.extension.combine
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InstallmentsViewModel(
    installmentRepository: IInstallmentRepository,
    transactionRepository: ITransactionRepository,
    categoryRepository: ICategoryRepository,
    invoiceRepository: IInvoiceRepository,
    private val installmentUiMapper: InstallmentUiMapper,
) : ViewModel() {

    private data class InstallmentWithDomain(
        val ui: InstallmentUi,
        val installment: Installment,
        val transactions: List<Transaction>,
    )

    private data class Facades(
        val lookup: TransactionFacadeLookup,
        val invoicesByDimension: Map<Long, Invoice>,
    )

    // What the ledger cannot name: the category behind a leg's dimension and the
    // invoice behind the card leg's (design D6). Closed included — this renders
    // history, it does not offer a choice.
    private val facades = combine(
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        invoiceRepository.observeAllInvoices(),
    ) { categories, installments, invoices ->
        Facades(
            lookup = TransactionFacadeLookup.of(categories, installments),
            invoicesByDimension = invoices.mapNotNull { invoice ->
                invoice.dimensionId?.let { it to invoice }
            }.toMap(),
        )
    }

    private val selectedInstallmentIndex = MutableStateFlow(0)
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val selectedType = MutableStateFlow<TransactionType?>(null)
    private val selectedFilter = MutableStateFlow(InstallmentFilter.ACTIVE)

    // Each installment paired with the domain transactions it was mapped from: the
    // flat UI model carries no graph, and the delete modal still needs the domain.
    private val allInstallmentsUi = combine(
        installmentRepository.observeAllInstallments(),
        transactionRepository.observeAllTransactions(),
        facades,
    ) { installments, transactions, facades ->
        val transactionsByInstallmentId = transactions
            .filter { it.installmentId != null }
            .groupBy { checkNotNull(it.installmentId) }

        installments
            .mapNotNull { installment ->
                val installmentTransactions =
                    transactionsByInstallmentId[installment.id].orEmpty()

                installmentUiMapper.toUi(
                    installment = installment,
                    transactions = installmentTransactions,
                    lookup = facades.lookup,
                    invoicesByDimension = facades.invoicesByDimension,
                )?.let { ui ->
                    InstallmentWithDomain(
                        ui = ui,
                        installment = installment,
                        transactions = installmentTransactions
                            .sortedBy { it.installmentNumber ?: Int.MAX_VALUE },
                    )
                }
            }
            .sortedWith(
                compareByDescending<InstallmentWithDomain> {
                    it.ui.latestTransactionDate
                }.thenByDescending { it.installment.id }
            )
    }

    val uiState = combine(
        allInstallmentsUi,
        selectedInstallmentIndex,
        selectedCategory,
        selectedType,
        selectedFilter,
        facades,
    ) { installmentsAll, selectedIndex, category, type, filter, facades ->
        val filtered = when (filter) {
            InstallmentFilter.ACTIVE -> installmentsAll.filter { it.ui.isActive }
            InstallmentFilter.COMPLETED -> installmentsAll.filter { !it.ui.isActive }
            InstallmentFilter.ALL -> installmentsAll
        }

        if (filtered.isEmpty()) {
            return@combine InstallmentsUiState.Empty(selectedFilter = filter)
        }

        val safeSelectedIndex = selectedIndex
            .coerceAtLeast(0)
            .coerceAtMost(filtered.size - 1)

        val selected = filtered.getOrNull(safeSelectedIndex)
        val selectedTransactions = selected?.transactions.orEmpty()

        val categories = selectedTransactions
            .mapNotNull { facades.lookup.categoryOf(it) }
            .distinctBy { it.id }

        // Filtering lives here, as it does on every sibling screen — the state is
        // handed the finished list instead of deriving it on read.
        val rows = selectedTransactions
            .filter { transaction ->
                category == null || transaction.nominalDimensionId == category.dimensionId
            }
            .filter { transaction ->
                type == null || transaction.primaryEntry?.let {
                    deriveTransactionType(it.amount, transaction.entries)
                } == type
            }
            .mapNotNull { installmentUiMapper.toRowUi(it, facades.lookup, facades.invoicesByDimension) }

        InstallmentsUiState.Content(
            installments = filtered.map { it.ui },
            selectedInstallmentIndex = safeSelectedIndex,
            transactions = rows,
            selectedDomainInstallment = selected?.installment,
            selectedDomainTransactions = selectedTransactions,
            selectedCategory = category,
            selectedType = type,
            selectedFilter = filter,
            categories = categories,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InstallmentsUiState.Loading(),
    )

    fun onAction(action: InstallmentsAction) {
        when (action) {
            is InstallmentsAction.SelectInstallment -> {
                selectedInstallmentIndex.update { action.index.coerceAtLeast(0) }
                selectedCategory.value = null
                selectedType.value = null
            }

            is InstallmentsAction.SelectCategory -> {
                selectedCategory.value = action.category
            }

            is InstallmentsAction.SelectType -> {
                selectedType.value = action.type
            }

            is InstallmentsAction.SelectFilter -> {
                selectedFilter.value = action.filter
                selectedInstallmentIndex.value = 0
                selectedCategory.value = null
                selectedType.value = null
            }
        }
    }
}
