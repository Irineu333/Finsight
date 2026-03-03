package com.neoutils.finsight.ui.screen.reports

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

class GenerateReportPreviewUseCase(
    private val operationRepository: IOperationRepository,
    private val transactionRepository: ITransactionRepository,
    private val formatter: CurrencyFormatter,
) {

    suspend operator fun invoke(
        request: ReportRequest,
    ): GeneratedReportPreview {
        return when (request) {
            is ReportRequest.AccountBalance -> generateAccountBalance(request)
            is ReportRequest.InvoiceStatement -> generateInvoiceStatement(request)
            is ReportRequest.TransactionsByPeriod -> generateTransactionsCsv(request)
        }
    }

    private suspend fun generateAccountBalance(
        request: ReportRequest.AccountBalance,
    ): GeneratedReportPreview {
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

        val content = buildString {
            appendLine("BALANCO DA CONTA")
            appendLine("Conta: ${request.account.name}")
            appendLine("Periodo: ${formatDate(request.startDate)} a ${formatDate(request.endDate)}")
            appendLine()
            appendLine("Saldo inicial: ${formatter.format(initialBalance)}")
            appendLine("Entradas: ${formatter.format(income)}")
            appendLine("Saidas: ${formatter.format(expense)}")
            appendLine("Ajustes: ${formatter.formatWithSign(adjustment)}")
            appendLine("Saldo final: ${formatter.format(finalBalance)}")
            appendLine()
            appendLine("Movimentacoes")
            appendLine("data | tipo | descricao | impacto")

            periodTransactions.forEach { (operation, transaction) ->
                appendLine(
                    "${formatDate(transaction.date)} | " +
                        "${operation.kind.name} ${transaction.type.name} | " +
                        "${operation.label} | ${formatter.formatWithSign(transaction.signedImpact())}"
                )
            }
        }

        return GeneratedReportPreview(
            title = "Balanco da conta",
            subtitle = "${request.account.name} • ${formatDate(request.startDate)} a ${formatDate(request.endDate)}",
            requestedFormat = request.format,
            suggestedFileName = "balanco-conta-${slug(request.account.name)}-${request.startDate}-${request.endDate}.pdf",
            mimeType = "application/pdf",
            content = content,
            highlights = listOf(
                ReportHighlight("Saldo inicial", formatter.format(initialBalance)),
                ReportHighlight("Entradas", formatter.format(income)),
                ReportHighlight("Saidas", formatter.format(expense)),
                ReportHighlight("Saldo final", formatter.format(finalBalance)),
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
                    columns = listOf("Data", "Tipo", "Descricao", "Impacto"),
                    rows = periodTransactions.map { (operation, transaction) ->
                        listOf(
                            formatDate(transaction.date),
                            "${operation.kind.name} ${transaction.type.name}",
                            operation.label,
                            formatter.formatWithSign(transaction.signedImpact()),
                        )
                    }
                ),
            ),
        )
    }

    private suspend fun generateInvoiceStatement(
        request: ReportRequest.InvoiceStatement,
    ): GeneratedReportPreview {
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

        val content = buildString {
            appendLine("FATURA")
            appendLine("Cartao: ${request.creditCard.name}")
            appendLine("Competencia: ${formatYearMonth(request.invoice.dueMonth)}")
            appendLine("Status: ${request.invoice.status.name}")
            appendLine("Fechamento: ${formatDate(request.invoice.closingDate)}")
            appendLine("Vencimento: ${formatDate(request.invoice.dueDate)}")
            appendLine()
            appendLine("Gastos: ${formatter.format(expense)}")
            appendLine("Adiantamentos: ${formatter.format(advancePayment)}")
            appendLine("Ajustes: ${formatter.formatWithSign(adjustment)}")
            appendLine("Total: ${formatter.format(total)}")
            appendLine()
            appendLine("Lancamentos")
            appendLine("data | tipo | descricao | valor")

            operations.forEach { operation ->
                val relatedTransactions = operation.transactions.filter { it.invoice?.id == request.invoice.id }
                relatedTransactions.forEach { transaction ->
                    appendLine(
                        "${formatDate(transaction.date)} | " +
                            "${operation.kind.name} ${transaction.type.name} | " +
                            "${operation.label} | ${formatter.formatWithSign(transaction.signedImpact())}"
                    )
                }
            }
        }

        return GeneratedReportPreview(
            title = "Fatura",
            subtitle = "${request.creditCard.name} • ${formatYearMonth(request.invoice.dueMonth)}",
            requestedFormat = request.format,
            suggestedFileName = "fatura-${slug(request.creditCard.name)}-${request.invoice.dueMonth}.pdf",
            mimeType = "application/pdf",
            content = content,
            highlights = listOf(
                ReportHighlight("Gastos", formatter.format(expense)),
                ReportHighlight("Adiantamentos", formatter.format(advancePayment)),
                ReportHighlight("Ajustes", formatter.formatWithSign(adjustment)),
                ReportHighlight("Total", formatter.format(total)),
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
                    }
                ),
            ),
        )
    }

    private suspend fun generateTransactionsCsv(
        request: ReportRequest.TransactionsByPeriod,
    ): GeneratedReportPreview {
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

        return GeneratedReportPreview(
            title = "Transacoes por periodo",
            subtitle = "${formatDate(request.startDate)} a ${formatDate(request.endDate)}",
            requestedFormat = request.format,
            suggestedFileName = "transacoes-${request.startDate}-${request.endDate}.csv",
            mimeType = "text/csv",
            content = content,
            highlights = listOf(
                ReportHighlight("Periodo", "${formatDate(request.startDate)} - ${formatDate(request.endDate)}"),
                ReportHighlight("Linhas", transactions.size.toString()),
                ReportHighlight(
                    "Entradas",
                    formatter.format(transactions.filter { it.type.isIncome }.sumOf { it.amount })
                ),
                ReportHighlight(
                    "Saidas",
                    formatter.format(transactions.filter { it.type.isExpense }.sumOf { it.amount })
                ),
            ),
            sections = listOf(
                ReportSection(
                    title = "Preview do CSV",
                    columns = listOf("Data", "Tipo", "Descricao", "Conta/Cartao", "Valor"),
                    rows = transactions.take(12).map { transaction ->
                        val operation = transaction.operationId?.let { operationById[it] }
                        listOf(
                            formatDate(transaction.date),
                            "${transaction.target.name} ${transaction.type.name}",
                            operation?.title ?: transaction.title ?: transaction.category?.name.orEmpty(),
                            transaction.account?.name ?: transaction.creditCard?.name.orEmpty(),
                            formatter.formatWithSign(transaction.signedImpact()),
                        )
                    }
                )
            ),
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

data class GeneratedReportPreview(
    val title: String,
    val subtitle: String,
    val requestedFormat: ReportFormat,
    val suggestedFileName: String,
    val mimeType: String,
    val content: String,
    val highlights: List<ReportHighlight>,
    val sections: List<ReportSection>,
)

data class ReportHighlight(
    val label: String,
    val value: String,
)

data class ReportSection(
    val title: String,
    val body: List<String> = emptyList(),
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)
