---
area: mcp
severity: medium
type: ux
---

# Capacidades que a pessoa tem na tela e o agente não tem pela superfície

## Invariante

O que uma pessoa faz numa tela do app, um agente faz pela superfície MCP — salvo o que uma decisão
registrada recusa. É o princípio que `mcp-tool-surface` escreve como *"a superfície é de
**apresentação**, com um agente no lugar da tela"*, e é o que dá sentido às quatro famílias:
elas são as quatro partes de uma tela — as figuras do topo, a lista, o formulário e os botões.

Hoje é falso em cada ponto da `## Evidência`, e nenhum deles foi recusado: as dezesseis entradas de
`McpSurface.exclusions` não nomeiam nenhum, e os requisitos que proíbem oferecer algo — taxa de
câmbio, moeda base, administrar o servidor — não alcançam nenhum.

Eles são de três feitios. Operações e leituras que a pessoa tem e o agente não; parâmetros que a
tela oferece e a ferramenta correspondente não aceita; e uma exclusão que declara coberto o que a
superfície não produz.

## Mecânica

A superfície não foi desenhada com esses buracos: sete deles nasceram depois dela.

`8f087f3aa` (16/08) fecha a superfície em cinquenta e seis ferramentas. Entre 21 e 27 de agosto o
app ganha sete capacidades, em commits que **não tocam `feature/mcp`** — e nada as relaciona à
superfície. `McpSurfaceIsClosedTest` compara o registro com `McpSurface.offered` nos dois sentidos,
o que garante que nenhuma ferramenta entra ou some sem decisão, e nunca olha para o app; o teste
das exclusões confere que quem está na lista tem motivo escrito, não que a lista esteja completa.
`feature/mcp` teve quarenta e um commits entre 16 e 19/08, ficou catorze dias sem edição, e voltou
em 2 de setembro com cinco correções internas. As sete capacidades atravessaram esse silêncio
inteiras.

As demais já existiam quando a superfície fechou, e ficaram de fora sem que nada as recusasse.

## Evidência

### Operações e leituras que o agente não alcança

As que nasceram depois do fechamento — cada linha é um commit sem nenhum arquivo de
`feature/mcp`:

- **Corrigir uma transferência** — `f0366f3de` (22/08), `UpdateTransferUseCase`
- **Corrigir um pagamento parcial de fatura** — `ca782deb1` (22/08),
  `UpdateAdvanceInvoicePaymentUseCase`; `2fa3065e5` (22/08) roteia as duas em `editFormFor()`, e
  `UpdateTransactionTool` recusa ambas. A recusa está registrada em
  `the-agent-refuses-an-edit-the-screen-now-offers.md`, que discute as quatro afirmações de KDoc;
  o que falta ali é a capacidade
- **As faturas a liquidar do mês** — `073289ced` (21/08),
  `IInvoiceRepository.observeInvoicesToSettle()`, consumida por `DashboardViewModel`.
  `get_card_overview` responde por cartão e `get_pending_recurring` só por templates
- **A categoria contra a própria média** — `86ef5b126` (24/08),
  `CalculateCategoryOverviewUseCase`: janela de até doze meses, média e variação; consumida por
  `ViewCategoryViewModel`
- **O realizado e o previsto de um mês de recorrências** — `c5eb3af04` (25/08),
  `GetRecurringMonthOverviewUseCase`. `get_month_summary` não substitui: exclui recorrentes não
  postados por desenho
- **Os ciclos de um mês em quatro grupos** — `84e8fafd9` (27/08), `GetRecurringCyclesUseCase`
  (`PENDING`, `UPCOMING`, `POSTED`, `SKIPPED`). `AgentRecurring` carrega só `is_pending`, e os
  outros três não são deriváveis dele

Nenhuma das quatro leituras é citada em qualquer arquivo sob `feature/mcp/impl/src/jvmMain`.

E as que já existiam quando a superfície fechou:

- **Ler o acervo de taxas** — nenhuma ferramenta lista as moedas registradas, as taxas em vigor, ou
  responde se o câmbio está velho, o que as telas de `feature/settings` fazem por
  `ExchangeRatesViewModel` e `RateSyncStatus`. `McpToolDependencies` toma apenas
  `IBaseCurrencyRepository`, e só para desempatar os dois lados de uma operação cross-currency
  (`McpToolDependencies.baseCurrency`). As duas exclusões `WITHHELD` proíbem **escrever** taxa e
  trocar a moeda base, e a primeira afirma o contrário sobre a leitura: *"The agent reads the rate
  that was applied; it does not write one"*
- **Perguntar se algo pode ser apagado ou só arquivado** — `ResolveCategoryRetirabilityUseCase` e
  `ResolveRecurringRetirabilityUseCase` estão nas `api` e são alcançados apenas como texto de
  recusa, dentro de `DeleteCategoryTool` e `DeleteRecurringTool`. As telas perguntam antes, por
  `ViewCategoryUiState.retireAction` e `ViewRecurringUiState.retireAction`

### Parâmetros que a tela oferece e a ferramenta não aceita

- `TransferTool.inputSchema` — cinco parâmetros, e o título da transferência não é nenhum deles.
  Nasceu em `c48076142` (22/08), depois do fechamento; registrado em
  `a-transfer-the-agent-records-is-born-without-a-title.md`
- `CreateBudgetTool.call()` — fixa `AppIcon.BUDGET.key`, e `UpdateBudgetTool.call()` repassa o
  armazenado, enquanto `CreateBudgetUseCase.invoke()` e `UpdateBudgetUseCase.invoke()` tomam
  `iconKey` e `BudgetFormAction.IconSelected` é a escolha na tela. A exclusão de ícones nomeia
  *"accounts, cards and categories"* — três entidades onde o app tem quatro
- `ListTransactionsTool.inputSchema` — `month`, `account_id`, `card_id`, `category_id`, `nature`,
  `order_by`, `limit`, `offset`. `TransactionsFilters` corta por `subject` (uma categoria **ou a
  ausência de uma**), `target`, `recurringOnly` e `installmentOnly`, e nenhum desses é exprimível;
  também não há `installment_id` nem `invoice_id`
- `GetInvoiceTool.inputSchema` — `id`, `order_by`, `limit`, `offset`: nenhum dos filtros que a tela
  da fatura aplica por `InvoiceTransactionsAction.SelectSubject`, `.ToggleRecurring` e
  `.ToggleInstallment`
- `ListRecurringTool.inputSchema` — `as_of` e `include_archived`, sem a natureza que
  `RecurringFilter` dá à tela
- `GetMonthSummaryTool.inputSchema` — `month` e `compare_to`, sem o perímetro que
  `TransactionScope` dá à tela

### A exclusão que declara coberto o que a superfície não produz

A entrada *"Configuring, rendering and exporting a report"* justifica-se em `McpSurface.exclusions`
com *"`get_report_stats` answers with the figures; assembling a document is a visual artefact
rather than data"*. As figuras que ela dá por cobertas:

- `GetReportStatsTool.inputSchema` aceita `from`, `to`, `account_ids` e `card_id`. Não há como
  nomear faturas, e `ReportViewerViewModel` produz `Stats.Invoice` — com `advancePayment` e
  `adjustment`, que a ferramenta não conhece em figura nenhuma
- `CalculateReportCategorySpendingUseCase.forDimensions()` decompõe categorias sob um conjunto de
  faturas; `GetCategorySpendingTool`, `GetCategoryIncomeTool` e `GetSpendingBreakdownTool` aceitam
  só `month`, sem intervalo e sem perímetro

## Consequência

Um agente responde *"não consigo"* a doze pedidos que a pessoa resolve num toque, e as quatro
leituras são justamente as que o app calcula porque não são deriváveis de uma listagem sem erro —
um agente que precise delas responde sem elas ou soma linhas à mão, que é o que a família das
perguntas existe para evitar.

Duas se agravam por combinação: uma transferência que o agente registra nasce sem título, e é a
mesma superfície que não lhe dá como corrigi-la depois.

E a distância cresce sozinha. Onze dias de desenvolvimento normal produziram sete lacunas; a
varredura que abriu esta issue as encontrou, e nada impede que a próxima semana produza outras
sete.

## Sugestão

Oferecer as capacidades — é o que fecha o invariante, e é decisão de escopo que passa pelo que o
`openspec` já declara sobre a superfície. As operações e leituras pedem ferramentas novas; os
parâmetros são acréscimo a schemas existentes; e a exclusão do relatório não é ampliação nenhuma:
ou ela para de prometer as figuras, ou `get_report_stats` passa a produzi-las.

**Três já estão decididas** por quem é dono da superfície: a correção de uma transferência, a
correção de um pagamento de fatura e o título da transferência entram. As demais seguem caso a
caso.

Duas notas para quem for fazer. `mcp-tool-surface` exige que uma operação sem use case seja
extraída para o módulo dono antes de virar ferramenta — as sete já têm dono, então nenhuma pede
regra nova. E `UpdateTransferUseCase` e `UpdateAdvanceInvoicePaymentUseCase` vivem nos `impl` das
suas features, alcançados pelas `Entry`; a superfície consome `api`, e é onde a decisão de promover
aparece.

Separadamente, vale decidir o que impede a repetição. Enumerar as capacidades por uma fonte que o
compilador conheça — os use cases das `api` — e exigir que cada um esteja consumido por uma
ferramenta ou nomeado numa exclusão faria um use case novo doer no momento em que nasce. Duas
ressalvas: um `grep` por use cases de `feature/*/api` fora de `McpToolDependencies` devolve treze,
e nem todos são capacidades — alguns são internos a outro use case, outros já estão declarados fora
—, e a fronteira de uma feature não é só o que ela expõe como use case: `observeInvoicesToSettle` é
método de repositório e escaparia inteiro à varredura.

Não vinculante — quem corrige decide.
