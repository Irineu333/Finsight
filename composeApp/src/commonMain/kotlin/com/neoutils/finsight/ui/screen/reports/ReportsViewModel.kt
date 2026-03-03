@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ReportsViewModel(
    accountRepository: IAccountRepository,
    creditCardRepository: ICreditCardRepository,
    invoiceRepository: IInvoiceRepository,
    private val generateReportPreviewUseCase: GenerateReportPreviewUseCase,
) : ViewModel() {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val initialStartDate = LocalDate(today.year, today.month, 1)

    private val selectedReportType = MutableStateFlow(ReportType.ACCOUNT_BALANCE)
    private val selectedAccountId = MutableStateFlow<Long?>(null)
    private val selectedCreditCardId = MutableStateFlow<Long?>(null)
    private val selectedInvoiceId = MutableStateFlow<Long?>(null)
    private val startDate = MutableStateFlow(initialStartDate)
    private val endDate = MutableStateFlow(today)

    private val accountsFlow = accountRepository.observeAllAccounts()
    private val creditCardsFlow = creditCardRepository.observeAllCreditCards()

    private val selectedCreditCardFlow = combine(
        creditCardsFlow,
        selectedCreditCardId,
    ) { creditCards, creditCardId ->
        creditCards.firstOrNull { it.id == creditCardId } ?: creditCards.firstOrNull()
    }

    private val invoicesFlow = selectedCreditCardFlow.flatMapLatest { creditCard ->
        if (creditCard == null) {
            flowOf(emptyList())
        } else {
            invoiceRepository.observeInvoicesByCreditCard(creditCard.id)
        }
    }

    private val selectionFlow = combine(
        selectedReportType,
        selectedAccountId,
        selectedCreditCardId,
        selectedInvoiceId,
        startDate,
        endDate,
    ) { reportType, accountId, creditCardId, invoiceId, startDate, endDate ->
        ReportsSelection(
            reportType = reportType,
            accountId = accountId,
            creditCardId = creditCardId,
            invoiceId = invoiceId,
            startDate = startDate,
            endDate = endDate,
        )
    }

    val uiState = kotlinx.coroutines.flow.combine(
        accountsFlow,
        creditCardsFlow,
        invoicesFlow,
        selectionFlow,
    ) { accounts, creditCards, invoices, selection ->
        val selectedAccount = accounts.firstOrNull { it.id == selection.accountId } ?: accounts.firstOrNull()
        val selectedCreditCard = creditCards.firstOrNull { it.id == selection.creditCardId } ?: creditCards.firstOrNull()
        val selectedInvoice = invoices.firstOrNull { it.id == selection.invoiceId } ?: invoices.firstOrNull()

        ReportsUiState(
            reportType = selection.reportType,
            accounts = accounts,
            selectedAccount = selectedAccount,
            creditCards = creditCards,
            selectedCreditCard = selectedCreditCard,
            invoices = invoices,
            selectedInvoice = selectedInvoice,
            startDate = selection.startDate,
            endDate = selection.endDate,
            reportRequest = buildRequest(
                reportType = selection.reportType,
                selectedAccount = selectedAccount,
                selectedCreditCard = selectedCreditCard,
                selectedInvoice = selectedInvoice,
                startDate = selection.startDate,
                endDate = selection.endDate,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState(
            startDate = initialStartDate,
            endDate = today,
        ),
    )

    fun selectReportType(reportType: ReportType) {
        selectedReportType.value = reportType
    }

    fun selectAccount(account: Account?) {
        selectedAccountId.value = account?.id
    }

    fun selectCreditCard(creditCard: CreditCard) {
        selectedCreditCardId.value = creditCard.id
        selectedInvoiceId.value = null
    }

    fun selectInvoice(invoice: Invoice) {
        selectedInvoiceId.value = invoice.id
    }

    fun updateStartDate(date: LocalDate) {
        startDate.update { date }
        if (date > endDate.value) {
            endDate.value = date
        }
    }

    fun updateEndDate(date: LocalDate) {
        endDate.update { date }
        if (date < startDate.value) {
            startDate.value = date
        }
    }

    suspend fun generateReport(
        request: ReportRequest,
    ): GeneratedReportPreview {
        return generateReportPreviewUseCase(request)
    }

    private fun buildRequest(
        reportType: ReportType,
        selectedAccount: Account?,
        selectedCreditCard: CreditCard?,
        selectedInvoice: Invoice?,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportRequest? {
        return when (reportType) {
            ReportType.ACCOUNT_BALANCE -> {
                selectedAccount?.let {
                    ReportRequest.AccountBalance(
                        account = it,
                        startDate = startDate,
                        endDate = endDate,
                    )
                }
            }

            ReportType.INVOICE -> {
                if (selectedCreditCard != null && selectedInvoice != null) {
                    ReportRequest.InvoiceStatement(
                        creditCard = selectedCreditCard,
                        invoice = selectedInvoice,
                    )
                } else {
                    null
                }
            }

            ReportType.TRANSACTIONS -> {
                ReportRequest.TransactionsByPeriod(
                    startDate = startDate,
                    endDate = endDate,
                )
            }
        }
    }

    private data class ReportsSelection(
        val reportType: ReportType,
        val accountId: Long?,
        val creditCardId: Long?,
        val invoiceId: Long?,
        val startDate: LocalDate,
        val endDate: LocalDate,
    )
}
