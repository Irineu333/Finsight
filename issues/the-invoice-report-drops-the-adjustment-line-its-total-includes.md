---
area: report
severity: medium
type: data
---

# O relatório de fatura calcula o ajuste e não desenha linha alguma para ele

## Cenário

**DADO** uma fatura com R$ 200,00 de compras, nenhuma antecipação e um ajuste de saldo de
+R$ 100,00 (que eleva a dívida)
**QUANDO** o usuário gera o relatório daquela fatura — na tela e no documento exportado
**ENTÃO** o resumo diz `Saída −R$ 200,00`, `Pago +R$ 0,00` e `Aberta R$ 300,00`, e nada na
página explica os R$ 100,00 de diferença
**DEVERIA** exibir a linha do ajuste, como a própria tela da fatura já faz

## Mecânica

As quatro figuras saem do razão no mesmo bloco. `expense`, `advancePayment` e `adjustment` vêm
de `IEntryRepository.flowsByDimensionByCurrency()` (`EntryDao.periodTotalsByDimension()`);
`total` vem de `owedByDimensionByCurrency()`, o saldo natural da dimensão negado. As três
primeiras particionam Σ entries, então a identidade é exata:

```
Aberta = Saída − Pago − ajuste
```

`ReportViewerViewModel` calcula as quatro e as guarda em `ReportViewerUiState.Stats.Invoice`.
**Nada lê `adjustment`.** A ramificação `is ReportViewerUiState.Stats.Invoice ->` de
`ReportContextCard` desenha três linhas — `report_viewer_summary_invoice_expense`,
`report_viewer_summary_invoice_total`, `report_viewer_summary_advance_payment` — e a
ramificação homônima de `toReportLayout()` monta os mesmos três `ReportSummaryItem`. Não existe
sequer chave de string para um ajuste no bloco `report_viewer_summary_*`: o campo nunca teve
destino.

O KDoc que acompanha a construção afirma o contrário — que "as linhas da fatura seguem a mesma
regra das linhas de conta deste mesmo relatório: o gasto subtrai, a antecipação soma, e só o
ajuste precisa ter a direção explicitada" —, descrevendo uma linha que não existe. É por isso
que `adjustment` recebe `DisplayAmount.explicitSign` e ninguém nota que a política de sinal não
chega a lugar nenhum.

A mesma escrita, lida pela tela da fatura, **ganha** a linha: `InvoiceSummary` expõe
`mustShowAdjustment = adjustment.value != 0.0`, e `InvoiceTransactionsScreen` desenha
`SummaryRow(invoice_transactions_adjustments)` entre as antecipações e o total exatamente
quando ele vale. Duas superfícies sobre a mesma fatura, uma que fecha a conta e outra que não.

*A spec `ledger-reporting` já obriga o **razão** a reportar o ajuste como classe própria — e ele
reporta. A classe morre acima dele, na superfície.*

## Evidência

- `feature/report/impl/.../viewer/ReportViewerViewModel.kt` — a construção de
  `Stats.Invoice(...)`: `adjustment = DisplayAmount.explicitSign(...)`, e o KDoc acima dela
- `feature/report/impl/.../viewer/ReportViewerUiState.kt` — `Stats.Invoice.adjustment`, a única
  declaração do campo
- `feature/report/impl/.../viewer/ReportContextCard.kt` — a ramificação
  `is ReportViewerUiState.Stats.Invoice ->`: `expense`, `total`, `advancePayment`, e nada mais
- `feature/report/impl/.../viewer/ReportExportLayout.kt` — `toReportLayout()`, mesmo ramo, os
  mesmos três itens
- `core/resources/.../values/strings.xml` — o bloco `report_viewer_summary_*`
  (`invoice_total`, `invoice_expense`, `advance_payment`): não há chave de ajuste em idioma nenhum
- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsUiState.kt` —
  `InvoiceSummary.mustShowAdjustment`, e `InvoiceTransactionsScreen.kt` — o `SummaryRow` que ele
  governa
- `core/ledger/.../dao/EntryDao.kt` — `periodTotalsByDimension()`, e
  `core/ledger/.../repository/EntryRepository.kt` — `owedByDimensionByCurrency()`, que juntos
  produzem a identidade acima

## Consequência

O documento que o usuário exporta e imprime tem três números que não fecham, sem nada na página
que justifique a diferença — e o relatório é justamente o artefato que sai do app e é lido por
quem não pode conferir na tela. Quem conferir contra a tela da fatura vê a linha "Ajustes" lá e
não aqui, e não tem como saber qual das duas leituras está incompleta.

## Sugestão

Desenhar a linha nas duas superfícies, com a mesma condição que a tela da fatura já usa
(`adjustment.value != 0.0`), e uma chave nova em `values` e `values-en`. Se a decisão for que o
relatório mostra só três linhas, então `Stats.Invoice.adjustment` deve sair — um campo calculado
que ninguém lê é o que permitiu a divergência passar. Não vinculante.
