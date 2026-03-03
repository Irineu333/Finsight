package com.neoutils.finsight.ui.screen.accounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class AccountsUiState(
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountIndex: Int = 0,
    val selectedMonth: YearMonth? = null,
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
    val recurring: List<Recurring> = emptyList(),
    val selectedRecurring: Recurring? = null,
)

data class AccountUi(
    val account: Account,
    val initialBalance: Double,
    val balance: Double,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
    val advancePayment: Double,
)
