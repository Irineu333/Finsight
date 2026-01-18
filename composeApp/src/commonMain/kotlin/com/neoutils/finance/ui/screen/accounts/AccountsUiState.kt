package com.neoutils.finance.ui.screen.accounts

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class AccountsUiState(
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountIndex: Int = 0,
    val selectedMonth: YearMonth? = null,
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
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
