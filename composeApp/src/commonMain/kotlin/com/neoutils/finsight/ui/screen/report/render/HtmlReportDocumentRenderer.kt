package com.neoutils.finsight.ui.screen.report.render

import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.domain.model.ReportTone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class HtmlReportDocumentRenderer : ReportDocumentRenderer {
    override fun render(layout: ReportLayout): ReportDocument {
        val html = buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\" />")
            appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />")
            appendLine("<title>${layout.title.escapeHtml()}</title>")
            appendLine("<style>")
            appendLine(
                """
                :root{
                  --bg:#f7f8fa;
                  --surface:#ffffff;
                  --text:#161a23;
                  --muted:#6b7280;
                  --border:#e6e8ef;
                  --primary:#1f6feb;
                  --positive:#0f9d58;
                  --negative:#d93025;
                  --neutral:#4b5563;
                }
                *{box-sizing:border-box;}
                body{
                  margin:0;
                  padding:24px;
                  background:var(--bg);
                  color:var(--text);
                  font-family:"IBM Plex Sans","Segoe UI",Arial,sans-serif;
                }
                .page{
                  max-width:900px;
                  margin:0 auto;
                  background:var(--surface);
                  border:1px solid var(--border);
                  border-radius:16px;
                  padding:28px;
                }
                h1,h2,h3,p{margin:0;}
                .header{
                  display:grid;
                  gap:10px;
                  padding-bottom:18px;
                  border-bottom:1px solid var(--border);
                }
                .meta{
                  display:flex;
                  flex-wrap:wrap;
                  gap:8px;
                  align-items:center;
                }
                .badge{
                  display:inline-flex;
                  align-items:center;
                  border-radius:999px;
                  border:1px solid rgba(31,111,235,.25);
                  background:rgba(31,111,235,.10);
                  color:var(--primary);
                  font-weight:600;
                  font-size:12px;
                  padding:4px 10px;
                }
                .label{font-size:18px;font-weight:600;}
                .period,.generated{font-size:13px;color:var(--muted);}
                .header-top{
                  display:flex;
                  justify-content:flex-end;
                }
                .summary{
                  margin-top:20px;
                  display:grid;
                  grid-template-columns:repeat(auto-fit,minmax(170px,1fr));
                  gap:12px;
                }
                .summary-item{
                  border:1px solid var(--border);
                  border-radius:12px;
                  padding:12px;
                }
                .summary-label{font-size:12px;color:var(--muted);}
                .summary-value{margin-top:4px;font-size:20px;font-weight:700;}
                .tone-positive{color:var(--positive);}
                .tone-negative{color:var(--negative);}
                .tone-neutral{color:var(--neutral);}
                .section{
                  margin-top:22px;
                  border-top:1px solid var(--border);
                  padding-top:16px;
                }
                .section-title{
                  font-size:16px;
                  font-weight:700;
                  margin-bottom:10px;
                }
                .table{
                  width:100%;
                  border-collapse:collapse;
                  font-size:14px;
                }
                .table th,.table td{
                  border-bottom:1px solid var(--border);
                  text-align:left;
                  padding:8px 6px;
                }
                .table th{font-size:12px;color:var(--muted);font-weight:600;}
                .table td.amount,.table th.amount{text-align:right;}
                .group-title{
                  margin-top:14px;
                  margin-bottom:6px;
                  color:var(--muted);
                  font-size:12px;
                  font-weight:600;
                }
                """.trimIndent()
            )
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<main class=\"page\">")

            appendLine("<header class=\"header\">")
            appendLine("<div class=\"header-top\">")
            appendLine("<p class=\"generated\">${layout.generatedAtLabel.escapeHtml()}</p>")
            appendLine("</div>")
            appendLine("<h1>${layout.title.escapeHtml()}</h1>")
            appendLine("<div class=\"meta\">")
            appendLine("<span class=\"badge\">${layout.context.badge.escapeHtml()}</span>")
            appendLine("<span class=\"label\">${layout.context.label.escapeHtml()}</span>")
            appendLine("</div>")
            appendLine("<p class=\"period\">${layout.context.period.escapeHtml()}</p>")
            appendLine("</header>")

            if (layout.summaryItems.isNotEmpty()) {
                appendLine("<section class=\"summary\">")
                layout.summaryItems.forEach { item ->
                    val toneClass = item.tone.toCssClass()
                    appendLine("<article class=\"summary-item\">")
                    appendLine("<p class=\"summary-label\">${item.label.escapeHtml()}</p>")
                    appendLine("<p class=\"summary-value $toneClass\">${item.value.escapeHtml()}</p>")
                    appendLine("</article>")
                }
                appendLine("</section>")
            }

            layout.sections.forEach { section ->
                when (section) {
                    is ReportLayoutSection.SpendingByCategory -> {
                        appendLine("<section class=\"section\">")
                        appendLine("<h2 class=\"section-title\">${section.title.escapeHtml()}</h2>")
                        appendLine("<table class=\"table\">")
                        appendLine(
                            "<thead><tr><th>${layout.labels.category.escapeHtml()}</th><th class=\"amount\">${layout.labels.amount.escapeHtml()}</th><th class=\"amount\">${layout.labels.percentage.escapeHtml()}</th></tr></thead>"
                        )
                        appendLine("<tbody>")
                        section.items.forEach { item ->
                            appendLine("<tr>")
                            appendLine("<td>${item.label.escapeHtml()}</td>")
                            appendLine("<td class=\"amount\">${item.amount.escapeHtml()}</td>")
                            appendLine("<td class=\"amount\">${item.percentage.escapeHtml()}</td>")
                            appendLine("</tr>")
                        }
                        appendLine("</tbody>")
                        appendLine("</table>")
                        appendLine("</section>")
                    }

                    is ReportLayoutSection.Transactions -> {
                        appendLine("<section class=\"section\">")
                        appendLine("<h2 class=\"section-title\">${section.title.escapeHtml()}</h2>")
                        section.groups.forEach { group ->
                            appendLine("<h3 class=\"group-title\">${group.dateLabel.escapeHtml()}</h3>")
                            appendLine("<table class=\"table\">")
                            appendLine(
                                "<thead><tr><th>${layout.labels.transaction.escapeHtml()}</th><th class=\"amount\">${layout.labels.amount.escapeHtml()}</th></tr></thead>"
                            )
                            appendLine("<tbody>")
                            group.items.forEach { item ->
                                appendLine("<tr>")
                                appendLine("<td>${item.title.escapeHtml()}</td>")
                                appendLine("<td class=\"amount ${item.tone.toCssClass()}\">${item.amount.escapeHtml()}</td>")
                                appendLine("</tr>")
                            }
                            appendLine("</tbody>")
                            appendLine("</table>")
                        }
                        appendLine("</section>")
                    }
                }
            }

            appendLine("</main>")
            appendLine("</body>")
            appendLine("</html>")
        }

        return ReportDocument(
            fileNameWithoutExtension = "report-${currentIsoDate()}",
            format = ReportDocument.Format.HTML,
            content = html.encodeToByteArray(),
        )
    }
}

private fun String.escapeHtml(): String {
    return buildString(length) {
        this@escapeHtml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

private fun ReportTone.toCssClass(): String {
    return when (this) {
        ReportTone.POSITIVE -> "tone-positive"
        ReportTone.NEGATIVE -> "tone-negative"
        ReportTone.NEUTRAL -> "tone-neutral"
    }
}

private fun currentIsoDate(): String {
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return today.toString()
}
