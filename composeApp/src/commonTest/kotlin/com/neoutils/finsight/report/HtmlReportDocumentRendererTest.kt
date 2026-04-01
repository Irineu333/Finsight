package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.CategoryItem
import com.neoutils.finsight.domain.model.ReportContext
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.domain.model.ReportSummaryItem
import com.neoutils.finsight.domain.model.ReportTableLabels
import com.neoutils.finsight.domain.model.ReportTone
import com.neoutils.finsight.domain.model.TransactionGroup
import com.neoutils.finsight.domain.model.TransactionItem
import com.neoutils.finsight.ui.screen.report.render.HtmlReportDocumentRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlReportDocumentRendererTest {
    private val renderer = HtmlReportDocumentRenderer()

    @Test
    fun renderBuildsHtmlWithSummaryAndSections() {
        val layout = ReportLayout(
            title = "Relatório",
            generatedAtLabel = "Gerado em: 2026-03-12",
            context = ReportContext(
                badge = "Conta",
                label = "Carteira",
                period = "01/03/2026 - 12/03/2026",
            ),
            labels = ReportTableLabels(
                category = "Categoria",
                transaction = "Transação",
                amount = "Valor",
                percentage = "%",
            ),
            summaryItems = listOf(
                ReportSummaryItem(label = "Saldo", value = "R$ 100,00", tone = ReportTone.POSITIVE),
            ),
            sections = listOf(
                ReportLayoutSection.SpendingByCategory(
                    title = "Gastos por Categoria",
                    items = listOf(
                        CategoryItem(
                            label = "Alimentação",
                            amount = "R$ 30,00",
                            percentage = "60.0%",
                        )
                    ),
                ),
                ReportLayoutSection.Transactions(
                    title = "Transações",
                    groups = listOf(
                        TransactionGroup(
                            dateLabel = "12/03/2026",
                            items = listOf(
                                TransactionItem(
                                    title = "Mercado",
                                    amount = "R$ 30,00",
                                    tone = ReportTone.NEGATIVE,
                                )
                            ),
                        )
                    ),
                ),
            ),
        )

        val result = renderer.render(layout)
        val html = result.content.decodeToString()

        assertEquals(ReportDocument.Format.HTML, result.format)
        assertTrue(html.contains("<h1>Relatório</h1>"))
        assertTrue(html.contains("Gastos por Categoria"))
        assertTrue(html.contains("Transações"))
        assertTrue(html.indexOf("Gastos por Categoria") < html.indexOf("Transações"))
    }

    @Test
    fun renderEscapesHtmlSensitiveCharacters() {
        val layout = ReportLayout(
            title = "Relatório <script>",
            generatedAtLabel = "Gerado em: 2026-03-12",
            context = ReportContext(
                badge = "Conta",
                label = "A&B",
                period = "01/03/2026 - 12/03/2026",
            ),
            labels = ReportTableLabels(
                category = "Categoria",
                transaction = "Transação",
                amount = "Valor",
                percentage = "%",
            ),
            summaryItems = emptyList(),
            sections = listOf(
                ReportLayoutSection.Transactions(
                    title = "Transações",
                    groups = listOf(
                        TransactionGroup(
                            dateLabel = "Hoje",
                            items = listOf(
                                TransactionItem(
                                    title = "<Mercado & Farmácia>",
                                    amount = "R$ 10,00",
                                    tone = ReportTone.NEUTRAL,
                                )
                            ),
                        )
                    ),
                )
            ),
        )

        val html = renderer.render(layout).content.decodeToString()

        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("A&amp;B"))
        assertTrue(html.contains("&lt;Mercado &amp; Farmácia&gt;"))
    }
}
