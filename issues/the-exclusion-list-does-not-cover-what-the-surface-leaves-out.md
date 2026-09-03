---
area: mcp
severity: medium
type: ux
---

# A lista de exclusões não cobre o que a superfície deixa de fora, e nada verifica que ela cobre

## Invariante

Toda capacidade do app aparece na superfície MCP de um dos dois jeitos: alcançada por uma
ferramenta de `McpSurface.offered`, ou nomeada em `McpSurface.exclusions` com a razão de estar
fora. É o que `mcp-tool-surface` exige no requisito *"A superfície é fechada, e o que fica de fora
é declarado"*, e o motivo escrito ali é que **a ausência não se manifesta**: uma capacidade
esquecida e uma capacidade recusada são indistinguíveis para quem lê a lista do que existe.

Hoje é falso em sete pontos, listados na `## Evidência`. Um deles é o inverso da omissão: uma
exclusão que declara coberto o que a superfície não produz.

## Mecânica

A metade *oferecida* do invariante é sustentada por teste nos dois sentidos —
`McpSurfaceIsClosedTest` compara `mcpTools()` com `McpSurface.offered`, e uma ferramenta a mais
ou a menos falha. A metade *excluída* não tem equivalente: o único teste que a toca
(`McpSurfaceIsClosedTest.every capability left out is left out for a stated reason`) verifica que
a lista não está vazia e que nenhuma entrada tem `capability` ou `reason` em branco, e
`the capabilities a requirement withholds are the ones declared as withheld` confere apenas as
três `WITHHELD` contra os requisitos que as proíbem.

Nenhum dos dois pergunta o que falta. A completude é mantida à mão, num documento que se declara
varrido (`docs/mcp-tool-surface.md`, seção *"O que fica de fora"*: *"Varrido contra as features do
app, não amostrado"*), e uma capacidade nova do app não faz nada ficar vermelho.

O ponto (d) é de outra espécie e não seria pego nem por um teste de completude: a exclusão existe,
está escrita, e afirma que as figuras já estão cobertas por uma ferramenta que não sabe produzi-las.

## Evidência

**(a) Ícone de orçamento** — a exclusão nomeia três entidades onde o app tem quatro:

- `McpSurface.exclusions` — *"Icons for accounts, cards and categories"*
- `CreateBudgetUseCase.invoke()` e `UpdateBudgetUseCase.invoke()` — ambos tomam `iconKey`
- `BudgetFormAction.IconSelected` — a escolha na tela
- `CreateBudgetTool.call()` — fixa `AppIcon.BUDGET.key`; `UpdateBudgetTool.call()` repassa
  `stored.iconKey`

**(b) Quatro leituras que as telas calculam e nenhuma ferramenta alcança** — nenhum arquivo sob
`feature/mcp/impl/src/jvmMain` cita qualquer uma delas:

- `CalculateCategoryOverviewUseCase` (`feature/categories/api`) — janela de até doze meses, média
  e variação de uma categoria; consumido por `ViewCategoryViewModel`
- `GetRecurringCyclesUseCase` (`feature/recurring/api`) — a partição em `PENDING`, `UPCOMING`,
  `POSTED` e `SKIPPED`; consumido por `RecurringViewModel`. `AgentRecurring` carrega só
  `is_pending`, e os outros três estados não são deriváveis dele
- `GetRecurringMonthOverviewUseCase` (`feature/recurring/api`) — realizado e previsto do mês;
  `get_month_summary` não substitui, porque exclui recorrentes não postados por desenho
- `IInvoiceRepository.observeInvoicesToSettle()` — as faturas cujo vencimento chegou e não foram
  pagas; consumido por `DashboardViewModel`

**(c) O acervo de taxas é ilegível** — as duas exclusões `WITHHELD` proíbem *escrever* taxa e
trocar a moeda base, e a primeira delas afirma *"The agent reads the rate that was applied; it does
not write one"*. Ler o acervo não é proibido por requisito nenhum, e também não existe:
`McpToolDependencies` toma apenas `IBaseCurrencyRepository`, e só para desempatar os dois lados de
uma operação cross-currency (`McpToolDependencies.baseCurrency`). Nenhuma ferramenta lista as
moedas registradas, as taxas em vigor, ou responde se o câmbio está velho — o que as telas de
`feature/settings` fazem por `ExchangeRatesViewModel` e `RateSyncStatus`.

**(d) A exclusão do relatório declara coberto o que a ferramenta não produz** — a entrada
*"Configuring, rendering and exporting a report"* justifica-se em `McpSurface.exclusions` com
*"`get_report_stats` answers with the figures; assembling a document is a visual artefact rather
than data"*. As figuras que ela dá por cobertas:

- `GetReportStatsTool.inputSchema` aceita `from`, `to`, `account_ids` e `card_id`. Não há como
  nomear faturas, e `ReportViewerViewModel` produz `Stats.Invoice` — com `advancePayment` e
  `adjustment`, que a ferramenta não conhece em figura nenhuma
- `CalculateReportCategorySpendingUseCase.forDimensions()` decompõe categorias sob um conjunto de
  faturas; `GetCategorySpendingTool`, `GetCategoryIncomeTool` e `GetSpendingBreakdownTool` aceitam
  só `month`, sem intervalo e sem perímetro

**(e) Filtros que as telas oferecem e as listagens não aceitam:**

- `TransactionsFilters` — `subject` (uma categoria **ou a ausência de uma**), `target`,
  `recurringOnly`, `installmentOnly`; `InvoiceTransactionsAction.SelectSubject`,
  `.ToggleRecurring`, `.ToggleInstallment` e `.SelectInvoiceForDueMonth` repetem os mesmos cortes
  sobre a fatura
- `ListTransactionsTool.inputSchema` — `month`, `account_id`, `card_id`, `category_id`, `nature`,
  `order_by`, `limit`, `offset`. Não exprime "sem categoria", "só recorrentes", "só parcelados",
  nem aceita `installment_id` ou `invoice_id`
- `GetInvoiceTool.inputSchema` — só `id`, `order_by`, `limit`, `offset`: nenhum dos filtros que a
  tela da fatura aplica
- `ListRecurringTool.inputSchema` — `as_of` e `include_archived`, sem a natureza que
  `RecurringFilter` dá à tela
- `GetMonthSummaryTool.inputSchema` — `month` e `compare_to`, sem o perímetro que `TransactionScope`
  dá à tela (contas, cartões, geral)

**(f) "Apagar ou arquivar?" só se descobre tentando** — `ResolveCategoryRetirabilityUseCase` e
`ResolveRecurringRetirabilityUseCase` estão nas `api` das suas features e são alcançados apenas
como texto de recusa, dentro de `DeleteCategoryTool` e `DeleteRecurringTool`. O agente não tem
como perguntar antes; as telas perguntam, por `ViewCategoryUiState.retireAction` e
`ViewRecurringUiState.retireAction`.

**(g) Editar uma transferência ou um pagamento de fatura** — `EditForm.editFormFor()` roteia
`TransactionLabel.TRANSFER` e `.PAYMENT` para `UpdateTransferUseCase` e
`UpdateAdvanceInvoicePaymentUseCase`, e `UpdateTransactionTool` recusa as duas. A recusa está
registrada em `the-agent-refuses-an-edit-the-screen-now-offers.md`; o que falta aqui é a outra
metade — a ausência não consta da lista de exclusões, então lê-se como esquecimento.

## Consequência

A lista existe para ser o único lugar onde se distingue o que foi recusado do que foi esquecido, e
quem a lê hoje conclui errado nos dois sentidos: sete capacidades ausentes parecem decididas, e uma
que a lista dá por coberta não existe. É o dano que o próprio KDoc de `McpSurface.exclusions`
descreve — *"a tool that was forgotten and a tool that was refused look exactly alike from the list
of what exists"* — acontecendo na metade da estrutura que ninguém verifica.

O custo prático é do agente. As quatro leituras de (b) são as que o app calcula justamente porque
não são deriváveis de uma listagem sem erro; um agente que precise delas ou responde sem elas ou
soma linhas à mão, que é o que a família das perguntas existe para evitar.

## Sugestão

São duas decisões separadas, e só a primeira é mecânica.

Fechar a metade não verificada: enumerar as capacidades do app por uma fonte que o compilador
conheça — os use cases das `api`, que já são a fronteira pública de cada feature — e exigir que
cada um esteja consumido por uma ferramenta ou nomeado numa exclusão. O `grep` que abriu esta
issue encontrou treze use cases de `feature/*/api` fora de `McpToolDependencies`, e três deles são
leituras de (b); os outros dez vão de operações internas a outro use case a capacidades já
declaradas fora. Um teste precisaria dessa distinção para não acusar falso positivo, e é ela que
decide se a ideia se sustenta. Note que a varredura por use case não alcançaria a quarta leitura
de (b): `observeInvoicesToSettle` é método de repositório, e a fronteira de uma feature não é
apenas o que ela expõe como use case.

Depois, caso a caso, decidir o que entra na superfície e o que entra na lista. (a), (b), (c), (e) e
(f) são ampliações de escopo e passam pelo que o `openspec` já declara. (d) é diferente e não
depende dessa decisão: a exclusão do relatório precisa ou parar de prometer as figuras, ou as
ferramentas precisam produzi-las.

Não vinculante — quem corrige decide.
