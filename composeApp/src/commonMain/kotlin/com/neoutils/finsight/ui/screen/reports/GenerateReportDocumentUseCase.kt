package com.neoutils.finsight.ui.screen.reports

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

class GenerateReportDocumentUseCase(
    private val operationRepository: IOperationRepository,
    private val transactionRepository: ITransactionRepository,
    private val formatter: CurrencyFormatter,
) {

    suspend operator fun invoke(
        request: ReportRequest,
    ): ReportDocument {
        return when (request) {
            is ReportRequest.AccountBalance -> generateAccountBalance(request)
            is ReportRequest.InvoiceStatement -> generateInvoiceStatement(request)
            is ReportRequest.TransactionsByPeriod -> generateTransactionsCsv(request)
        }
    }

    private suspend fun generateAccountBalance(
        request: ReportRequest.AccountBalance,
    ): ReportDocument.Pdf {
        val operations = operationRepository.getAllOperations()
        val accountTransactions = operations
            .flatMap { operation ->
                operation.transactions
                    .filter { transaction ->
                        transaction.target.isAccount && transaction.account?.id == request.account.id
                    }
                    .map { transaction -> operation to transaction }
            }

        val initialBalance = accountTransactions
            .filter { (_, transaction) -> transaction.date < request.startDate }
            .sumOf { (_, transaction) -> transaction.signedImpact() }

        val periodTransactions = accountTransactions
            .filter { (_, transaction) -> transaction.date in request.startDate..request.endDate }
            .sortedBy { (_, transaction) -> transaction.date }

        val income = periodTransactions
            .filter { (_, transaction) -> transaction.type.isIncome }
            .sumOf { (_, transaction) -> transaction.amount }

        val expense = periodTransactions
            .filter { (_, transaction) -> transaction.type.isExpense }
            .sumOf { (_, transaction) -> transaction.amount }

        val adjustment = periodTransactions
            .filter { (_, transaction) -> transaction.type.isAdjustment }
            .sumOf { (_, transaction) -> transaction.signedImpact() }

        val finalBalance = initialBalance + periodTransactions.sumOf { (_, transaction) ->
            transaction.signedImpact()
        }

        return ReportDocument.Pdf(
            title = "Balanco da conta",
            subtitle = "${request.account.name} • ${formatDate(request.startDate)} a ${formatDate(request.endDate)}",
            fileName = "balanco-conta-${slug(request.account.name)}-${request.startDate}-${request.endDate}.pdf",
            highlights = listOf(
                ReportMetric("Saldo inicial", formatter.format(initialBalance)),
                ReportMetric("Entradas", formatter.format(income)),
                ReportMetric("Saidas", formatter.format(expense)),
                ReportMetric("Saldo final", formatter.format(finalBalance)),
            ),
            sections = listOf(
                ReportSection(
                    title = "Resumo",
                    body = listOf(
                        "Conta: ${request.account.name}",
                        "Periodo: ${formatDate(request.startDate)} a ${formatDate(request.endDate)}",
                        "Ajustes: ${formatter.formatWithSign(adjustment)}",
                    ),
                ),
                ReportSection(
                    title = "Movimentacoes",
                    table = ReportTable(
                        columns = listOf("Data", "Tipo", "Descricao", "Impacto"),
                        rows = periodTransactions.map { (operation, transaction) ->
                            listOf(
                                formatDate(transaction.date),
                                "${operation.kind.name} ${transaction.type.name}",
                                operation.label,
                                formatter.formatWithSign(transaction.signedImpact()),
                            )
                        },
                    ),
                ),
            ),
        )
    }

    private suspend fun generateInvoiceStatement(
        request: ReportRequest.InvoiceStatement,
    ): ReportDocument.Pdf {
        val operations = operationRepository.getAllOperations()
            .filter { operation ->
                operation.targetInvoice?.id == request.invoice.id ||
                    operation.transactions.any { transaction -> transaction.invoice?.id == request.invoice.id }
            }
            .sortedBy { it.date }

        val invoiceTransactions = operations
            .flatMap { it.transactions }
            .filter { transaction ->
                transaction.invoice?.id == request.invoice.id &&
                    transaction.target == Transaction.Target.CREDIT_CARD
            }

        val expense = invoiceTransactions
            .filter { it.type.isExpense }
            .sumOf { it.amount }

        val advancePayment = invoiceTransactions
            .filter { it.type == Transaction.Type.INCOME && it.isInvoicePayment }
            .sumOf { it.amount }

        val adjustment = invoiceTransactions
            .filter { it.type.isAdjustment }
            .sumOf { it.amount }

        val total = invoiceTransactions.sumOf { -it.signedImpact() }

        return ReportDocument.Pdf(
            title = "Fatura",
            subtitle = "${request.creditCard.name} • ${formatYearMonth(request.invoice.dueMonth)}",
            fileName = "fatura-${slug(request.creditCard.name)}-${request.invoice.dueMonth}.pdf",
            highlights = listOf(
                ReportMetric("Gastos", formatter.format(expense)),
                ReportMetric("Adiantamentos", formatter.format(advancePayment)),
                ReportMetric("Ajustes", formatter.formatWithSign(adjustment)),
                ReportMetric("Total", formatter.format(total)),
            ),
            sections = listOf(
                ReportSection(
                    title = "Resumo",
                    body = listOf(
                        "Cartao: ${request.creditCard.name}",
                        "Competencia: ${formatYearMonth(request.invoice.dueMonth)}",
                        "Status: ${request.invoice.status.name}",
                        "Fechamento: ${formatDate(request.invoice.closingDate)}",
                        "Vencimento: ${formatDate(request.invoice.dueDate)}",
                    ),
                ),
                ReportSection(
                    title = "Lancamentos",
                    table = ReportTable(
                        columns = listOf("Data", "Tipo", "Descricao", "Valor"),
                        rows = operations.flatMap { operation ->
                            operation.transactions
                                .filter { it.invoice?.id == request.invoice.id }
                                .map { transaction ->
                                    listOf(
                                        formatDate(transaction.date),
                                        "${operation.kind.name} ${transaction.type.name}",
                                        operation.label,
                                        formatter.formatWithSign(transaction.signedImpact()),
                                    )
                                }
                        },
                    ),
                ),
            ),
        )
    }

    private suspend fun generateTransactionsCsv(
        request: ReportRequest.TransactionsByPeriod,
    ): ReportDocument.Csv {
        val operations = operationRepository.getAllOperations()
        val operationById = operations.associateBy { it.id }

        val transactions = transactionRepository.getAllTransactions()
            .filter { transaction ->
                transaction.date in request.startDate..request.endDate
            }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })

        val content = buildString {
            appendLine(
                listOf(
                    "transaction_id",
                    "operation_id",
                    "date",
                    "operation_kind",
                    "type",
                    "target",
                    "title",
                    "category",
                    "account",
                    "credit_card",
                    "invoice_due_month",
                    "amount",
                    "signed_amount",
                ).joinToString(",")
            )

            transactions.forEach { transaction ->
                val operation = transaction.operationId?.let { operationById[it] }
                appendLine(
                    listOf(
                        transaction.id.toString(),
                        transaction.operationId?.toString().orEmpty(),
                        transaction.date.toString(),
                        operation?.kind?.name.orEmpty(),
                        transaction.type.name,
                        transaction.target.name,
                        csvEscape(operation?.title ?: transaction.title ?: operation?.category?.name ?: ""),
                        csvEscape(transaction.category?.name.orEmpty()),
                        csvEscape(transaction.account?.name.orEmpty()),
                        csvEscape(transaction.creditCard?.name.orEmpty()),
                        transaction.invoice?.dueMonth?.toString().orEmpty(),
                        transaction.amount.toString(),
                        transaction.signedImpact().toString(),
                    ).joinToString(",")
                )
            }
        }

        return ReportDocument.Csv(
            title = "Transacoes por periodo",
            subtitle = "${formatDate(request.startDate)} a ${formatDate(request.endDate)}",
            fileName = "transacoes-${request.startDate}-${request.endDate}.csv",
            content = content,
        )
    }

    private fun csvEscape(value: String): String {
        val normalized = value.replace("\"", "\"\"")
        return "\"$normalized\""
    }

    private fun formatDate(date: LocalDate): String = dayMonthYear.format(date)

    private fun formatYearMonth(yearMonth: YearMonth): String {
        return "${yearMonth.year}-${yearMonth.month.number.toString().padStart(2, '0')}"
    }

    private fun slug(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "relatorio" }
    }
}
