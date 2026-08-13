# Tasks

## 1. Fundação: o tipo, a leitura do razão e as strings

**Barreira de entrada:** árvore limpa e `./gradlew jvmTest` verde no estado atual — é ele que define
o "antes" contra o qual as porcentagens de caracterização serão reafirmadas depois.

**Barreira de saída:** as três peças que ninguém consome ainda existem no código de produção. A
compilação dos módulos de teste fica **deliberadamente quebrada** ao fim deste grupo, porque 1.2
acrescenta um membro abstrato a `IEntryRepository` e os fakes que o implementam ainda não o conhecem —
o grupo 2 é quem restabelece `./gradlew jvmTest`. Nenhuma tarefa deste grupo altera comportamento
observável.

- [x] 1.1 Criar `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/SpendingSubject.kt`
      com a `sealed interface SpendingSubject` de dois valores — `data class Categorized(val category: Category)`
      e `data object Uncategorized` (D4). O `data object` é exigência de chave de mapa: identidade e
      `hashCode` estáveis. KDoc objetivo, sem texto voltado ao usuário e sem narrar a mudança. Nenhum
      consumidor é tocado aqui. *Verificação:* o arquivo existe, compila e nada mais no repositório o
      referencia.
- [x] 1.2 Abrir a leitura agregada mensal do razão (D5), nos três arquivos que a compõem e que nenhuma
      tarefa irmã toca — `core/ledger/.../database/dao/EntryDao.kt` (query nova espelhando
      `dimensionBalanceInMonth`, agrupando por `e.dimensionId, e.currency` e filtrando
      `a.type = :nominalType` e `substr(o.date, 1, 7) = :yearMonth`),
      `core/ledger/.../domain/repository/IEntryRepository.kt` (membro
      `totalsByDimensionInMonthByCurrency(month, nominalType): Map<Long?, MoneyByCurrency>`, com KDoc
      dizendo que a chave `null` é um grupo do mesmo agregado) e
      `core/ledger/.../database/repository/EntryRepository.kt` (a implementação). O filtro por natureza
      nominal é parte da assinatura, não uma cláusula opcional: sem ele `dimensionId IS NULL` alcançaria
      ativo, passivo e conversão. *Verificação:* a query nomeia apenas tabelas do `LedgerDatabase` e o
      membro é implementado pelo repositório de produção.
- [x] 1.3 Acrescentar a chave `category_spending_uncategorized` aos **dois** arquivos de recursos —
      `core/resources/src/commonMain/composeResources/values/strings.xml` (pt, o padrão) e
      `core/resources/src/commonMain/composeResources/values-en/strings.xml` (en) — junto de
      `category_spending_card_title`. Uma chave presente em só um dos arquivos é bug. *Verificação:* a
      chave existe nos dois arquivos e o `Res` gerado a expõe.

## 2. Restabelecer a compilação dos testes e cobrir a query nova

**Barreira de entrada:** 1.2 concluída — só há o que propagar depois que o membro existe.

**Barreira de saída:** `./gradlew jvmTest` volta a compilar e a passar. Nada de comportamento mudou
ainda: os testes existentes passam com os mesmos números de antes, e o teste novo cobre apenas a
leitura recém-aberta. As quatro tarefas escrevem em conjuntos de arquivos disjuntos.

- [x] 2.1 Propagar a assinatura nova nos fakes de `IEntryRepository` de `accounts`, `budgets` e
      `categories` (`AccountsEmptyStateTest`, `RetireAccountGuardsTest`, `AdjustBalanceUseCaseTest`,
      `ViewBudgetViewModelTest`, `BudgetClosedCategoryTest`, `CalculateBudgetProgressUseCaseTest`,
      `ViewCategoryViewModelTest`, `CalculateCategorySpendingUseCaseImplTest`, `DeleteCategoryGuardsTest`),
      seguindo a convenção local: `throw NotImplementedError()` onde o fake não usa a leitura.
- [x] 2.2 Propagar a mesma assinatura nos fakes de `creditcards` e `transactions`
      (`CreditCardsEmptyStateTest`, `InvoiceTransactionsFakes`, `AdjustInvoiceUseCaseTest`,
      `CalculateInvoiceOverviewsUseCaseTest`, `DeleteCreditCardUseCaseTest`, `FakeLedger`,
      `CalculateBalanceUseCaseTest`).
- [x] 2.3 Propagar a mesma assinatura nos fakes de `dashboard` e `report`
      (`DashboardTotalBalancePerimeterTest`, `DashboardAccountsOverviewTest`,
      `DashboardOverallBalanceStatsTest`, `DashboardPendingBalanceStatsTest`,
      `CalculateReportStatsUseCaseTest`, `ReportViewerViewModelCharacterizationTest`) — aqui apenas a
      assinatura; o conteúdo dos fakes de dashboard e os números do teste de caracterização são
      trabalho dos grupos 7.
- [x] 2.4 Criar um teste de query em `core/ledger/src/jvmTest/kotlin/com/neoutils/finsight/database/`,
      ao lado de `EntryCategoryQueryTest.kt`, para a leitura de 1.2, cobrindo os cenários da spec
      `ledger-reporting`: um mês inteiro em uma leitura (total por dimensão **e** total da ausência de
      dimensão no mesmo agregado, por moeda); compra de cartão sem categoria entrando pelo nominal
      `EXPENSE` (a dimensão da fatura pousa na perna `LIABILITY`); resíduo em `CONVERSION` ficando de
      fora; perna `ASSET` sem dimensão não duplicando o total. *Verificação:*
      `./gradlew jvmTest` executa a classe nova e ela passa.

## 3. O construtor único do detalhamento

**Barreira de entrada:** 1.1 e o grupo 2 concluídos — o tipo-soma existe e a suíte está verde.

**Barreira de saída:** existe **um** dono da regra de ordenação, sinal, escala, descarte de zeros e
cálculo da fatia, e `CategorySpending` já fala em `SpendingSubject`. A partir daqui os consumidores
(`CategorySpendingCard`, `DashboardComponentContent`, `ReportExportLayout`, os dois use cases) não
compilam até o grupo 6 — é a travessia esperada.

O grupo tem **uma única tarefa por desenho**: D6 diz que o construtor é o dono da regra, e os dois
produtores do grupo 4 o delegam. Escrever o construtor em paralelo com quem o chama reintroduziria
a duplicação que ele existe para eliminar. Renomear o campo e escrever o construtor também não se
separam: o construtor produz o item já com o campo novo.

- [x] 3.1 Em `core/model`: trocar `CategorySpending.category: Category` por `subject: SpendingSubject`
      (`domain/model/CategorySpending.kt`, mantendo o KDoc de `percentage` como está — ele já descreve
      o denominador desconhecido) e escrever, em `domain/usecase/` ao lado de `ConsolidateMoneyUseCase`,
      a extensão
      `suspend fun ConsolidateMoneyUseCase.spendingBreakdown(totals: Map<SpendingSubject, MoneyByCurrency>, displaySign: Int, on: LocalDate): List<CategorySpending>`.
      Ela aplica o sinal termo a termo, descarta o que soma zero (D3), constrói **uma** escala com
      `comparativeMagnitudes` incluindo `Uncategorized` como chave (D1), ordena por magnitude
      decrescente com `NEGATIVE_INFINITY` para quem não tem magnitude e **fixa `Uncategorized` em
      último lugar independentemente da magnitude** (D2), e calcula `shareOf * 100`. Não define
      rótulo algum (D8). *Verificação:* o corpo do construtor contém as cinco regras e nenhum dos dois
      use cases as reimplementa depois do grupo 4.

## 4. Os dois produtores e as decisões de apresentação

**Barreira de entrada:** 3.1 concluída — os produtores delegam a um construtor que já existe, com a
assinatura definitiva.

**Barreira de saída:** os dois caminhos que produzem `List<CategorySpending>` montam o seu próprio
`Map<SpendingSubject, MoneyByCurrency>` — perímetros continuam diferentes, e é assim que devem ser —
e delegam o resto; e as duas questões em aberto do desenho estão fechadas por escrito, antes de a
UI ser escrita no grupo 5. As quatro tarefas tocam arquivos disjuntos e nenhuma consome a saída da
outra.

- [x] 4.1 `feature/categories/impl/.../domain/usecase/CalculateCategorySpendingUseCaseImpl.kt`:
      substituir as N leituras por categoria (`dimensionBalanceInMonthByCurrency` em laço) pela
      leitura agregada de 1.2, resolver cada `dimensionId` não-nulo para a sua categoria em
      `SpendingSubject.Categorized`, mapear a chave `null` para `SpendingSubject.Uncategorized`, e
      delegar a `spendingBreakdown`. Vale para `CalculateCategorySpendingUseCaseImpl` (nominal
      `EXPENSE`) e `CalculateCategoryIncomeUseCaseImpl` (nominal `INCOME`), que dividem o mesmo
      arquivo e o mesmo `categoryTotals`. Manter a inclusão de categorias arquivadas e o descarte de
      dimensão órfã (D7). *Verificação:* o arquivo não contém mais `sortedByDescending` nem
      `comparativeMagnitudes`.
- [x] 4.2 `feature/report/impl/.../domain/usecase/CalculateReportCategorySpendingUseCase.kt`: no
      `build`, trocar o `mapNotNull` que descarta a chave `null` (hoje na linha 91, com o comentário
      "resolves to no category and drops out here") por uma tradução para `SpendingSubject` —
      `null` vira `Uncategorized`, `dimensionId` resolvido vira `Categorized`, `dimensionId` **não
      resolvido continua sendo descartado** (D7, e o comentário passa a dizer isso) — e delegar a
      `spendingBreakdown`. Os dois pontos de entrada (`invoke` por perspectiva e `forDimensions`)
      seguem com os seus perímetros e a sua data de consolidação. *Verificação:* o arquivo não contém
      mais `sortedByDescending` nem `comparativeMagnitudes`, e ambas as entradas passam pelo mesmo
      `build`.
- [x] 4.3 Fechar a questão aberta do par ícone/cor: submeter ao `ux-ui-designer` a proposta do desenho
      — `colorScheme.outline` sobre `surfaceContainerHighest`, apagado de propósito para não competir
      com categorias reais, mais um separador acima da linha — e registrar a decisão final no
      `design.md`, na seção Open Questions. *Verificação:* a questão deixa de estar aberta e nomeia
      tokens concretos que 5.1 possa aplicar.
- [x] 4.4 Fechar a questão aberta do `testTag`: decidir se a linha sem classificação ganha tag própria
      nesta entrega, lembrando que a tag só alcança o Maestro pela raiz de composição que chamou
      `Modifier.exposeTestTags()`. Registrar a decisão no `design.md`; se for sim, ela é aplicada em
      5.1. *Verificação:* a questão deixa de estar aberta com um sim ou um não explícito.

## 5. As superfícies que renderizam o item

**Barreira de entrada:** grupo 4 concluído — os produtores já entregam itens com `subject`, e o par
ícone/cor está fixado.

**Barreira de saída:** as três superfícies que consomem `CategorySpending` decidem conscientemente,
via `when` exaustivo, rótulo, ícone e clique de cada valor do eixo. Os três arquivos são disjuntos.
A compilação do app ainda está quebrada nos pontos de chamada — o grupo 6 fecha.

- [x] 5.1 `core/ui/.../ui/component/CategorySpendingCard.kt`: trocar os usos de `spending.category`
      (linhas 100, 123, 141, 182) por um `when` sobre `spending.subject`; `Uncategorized` usa
      `stringResource` da chave de 1.3 e o par ícone/cor decidido em 4.3, com o separador acima da
      linha; trocar `onCategoryClick: (Category) -> Unit` por `onSubjectClick: (SpendingSubject) -> Unit`
      com o ramo `Uncategorized` inerte e sem `clickable` (D9); aplicar o `testTag` se 4.4 disse sim.
      *Verificação:* o `when` é exaustivo e o card não referencia mais `CategorySpending.category`.
      **Ajustado depois da primeira entrega, por decisão do dono do produto** (ver Q1 do `design.md`):
      a linha usa a cor e o glifo da sua *natureza* — `TrendingDown` em vermelho `Expense` na despesa,
      `TrendingUp` em verde `Income` na receita — e o separador só aparece quando existem os dois
      grupos. Como o mesmo card renderiza gastos e receitas, ele passou a receber
      `type: Category.Type`; sem isso a receita sem categoria sairia vermelha, dizendo que o dinheiro
      saiu. Os quatro pontos de chamada de 6.1 e 6.2 informam a natureza que renderizam.
- [x] 5.2 `feature/report/impl/.../ui/screen/report/viewer/ReportExportLayout.kt`: acrescentar a
      `ReportExportStrings` o campo do rótulo sem classificação — a exportação recebe o texto já
      resolvido, o que a mantém fora do mundo `@Composable` (D8) — e trocar os usos de `category` nas
      linhas 113 e 128 por um `when` sobre `subject`. A ordem dos itens vem pronta do domínio: a
      exportação não reordena nada. *Verificação:* a lista exportada preserva a ordem recebida e a
      linha sem classificação sai por último.
- [x] 5.3 `feature/dashboard/impl/.../ui/screen/dashboard/DashboardPreviewFactory.kt`: adaptar as
      quatro construções de `CategorySpending` (linhas 171, 182, 202 e 213) para
      `SpendingSubject.Categorized` e acrescentar, em ao menos um dos detalhamentos de exemplo, uma
      linha `Uncategorized` com porcentagem coerente com as demais — o preview é onde a regra nova é
      vista sem rodar o app. *Verificação:* os previews do dashboard renderizam a linha nova.

## 6. Os pontos de chamada

**Barreira de entrada:** grupo 5 concluído — as assinaturas novas (`onSubjectClick`, o campo novo de
`ReportExportStrings`) já existem, e este grupo apenas as casa.

**Barreira de saída:** `./gradlew :app:android:assembleDebug` volta a compilar o app inteiro. Os dois
arquivos são disjuntos.

- [x] 6.1 `feature/dashboard/impl/.../ui/screen/dashboard/DashboardComponentContent.kt`: nos dois usos
      de `CategorySpendingCard` (gastos, linha 686; receitas, linha 710), trocar `onCategoryClick` por
      `onSubjectClick`, navegando para o modal da categoria só no ramo `Categorized` e não fazendo
      nada no ramo `Uncategorized` (D9). *Verificação:* nenhuma referência a `spending.category`
      restou no arquivo.
- [x] 6.2 `feature/report/impl/.../ui/screen/report/viewer/ReportViewerScreen.kt`: mesmo ajuste nos
      dois `CategorySpendingCard` (linhas 246 e 260) e preenchimento do campo novo de
      `ReportExportStrings` (linha 129) com a chave de 1.3, resolvida por `stringResource`.
      *Verificação:* a exportação e a tela recebem o mesmo texto para a linha sem classificação.

## 7. Testes

**Barreira de entrada:** grupo 6 concluído — o app compila e o comportamento novo está inteiro; só
então faz sentido afirmar números sobre ele.

**Barreira de saída:** `./gradlew jvmTest` verde, com os cenários das duas specs cobertos. Cada
tarefa escreve num arquivo de teste distinto.

- [x] 7.1 Novo teste do construtor único em `core/model/src/commonTest/.../domain/usecase/`, ao lado de
      `ConsolidateMoneyUseCaseTest`, cobrindo as regras que D6 concentrou: as fatias fecham o período
      (R$ 700,00 em categorias e R$ 300,00 sem categoria dão 70% e 30%); maior que todas e ainda assim
      por último; período inteiramente classificado produz exatamente o detalhamento de antes; período
      sem movimento não ganha linha zerada; e o caso multimoeda em que o total sem classificação está
      numa moeda que nenhuma taxa alcança — **nenhuma** linha tem porcentagem, porque o todo não é
      conhecido.
- [x] 7.2 `feature/categories/impl/.../domain/usecase/CalculateCategorySpendingUseCaseImplTest.kt`:
      reafirmar os casos existentes contra a leitura agregada nova e acrescentar os cenários do
      dashboard — despesa sem categoria aparecendo como última linha; a fatia de uma categoria que
      exibia 100% encolhendo sem que o seu valor monetário se mova; receita sem categoria aparecendo
      no detalhamento de receitas e não no de despesas; e despesa de **cartão** sem categoria entrando
      (o risco de perímetro que D5 evita).
- [x] 7.3 Novo teste do use case de relatório em
      `feature/report/impl/src/commonTest/.../domain/usecase/`, cobrindo a tradução da chave `null`
      para `Uncategorized`, a permanência do descarte de dimensão órfã (D7 — ela **não** é lavada no
      balde), a posição da linha nas duas entradas (`invoke` e `forDimensions`) e a não mistura entre
      despesa e receita.
- [x] 7.4 `feature/report/impl/.../ui/screen/report/viewer/ReportViewerViewModelCharacterizationTest.kt`:
      os percentuais mudam **por desenho** (D1). O teste deve ser **reafirmado linha a linha**, com
      cada novo valor conferido à mão contra o denominador que agora inclui o total sem classificação,
      e **nunca regenerado em massa** — regenerar transforma um teste de caracterização em carimbo, e
      é justamente aqui que uma regressão de valor monetário apareceria. Os valores monetários não
      podem mudar; só as fatias. *Verificação:* o diff do arquivo mostra alteração apenas nas
      porcentagens dos casos que têm movimento sem classificação.
      **Conferido linha a linha: nada a reafirmar.** O teste, tal como está no disco, não afirma
      porcentagem alguma — os seus dois casos passam `includeSpendingByCategory = false` e
      `includeIncomeByCategory = false` (linhas 105-106 e 186-187) e o fake de `IEntryRepository`
      lança em `totalsByDimensionByCurrency`, de modo que o detalhamento nunca é construído. Ele
      caracteriza o encaminhamento das *stats* da conta, e só. Nenhum valor foi tocado; o único
      ajuste no arquivo é a assinatura propagada em 2.3. A proteção que esta tarefa pretendia dar
      está em 7.1, 7.2 e 7.3, que afirmam as fatias diretamente.
- [x] 7.5 `feature/report/impl/.../ui/screen/report/viewer/ReportExportFootnoteTest.kt`: acomodar o
      campo novo de `ReportExportStrings` na fixture (linha 130) e afirmar que a exportação ordena
      como a tela, com a linha sem classificação por último.
- [x] 7.6 `feature/report/impl/.../ui/screen/report/viewer/ReportExportAdjustmentToneTest.kt`: acomodar
      o campo novo na fixture (linha 129) e afirmar que a linha sem classificação não recebe o
      tratamento visual de ajuste — ela é um valor do eixo, não um ajuste.
- [x] 7.7 Reafirmar os quatro testes de `feature/dashboard/impl` que fingem os use cases de categoria
      (`DashboardOverallBalanceStatsTest`, `DashboardTotalBalancePerimeterTest`,
      `DashboardPendingBalanceStatsTest`, `DashboardAccountsOverviewTest`): os fakes de
      `CalculateCategorySpendingUseCase` e `CalculateCategoryIncomeUseCase` passam a devolver
      `CategorySpending` com `subject`, e ao menos um deles devolve uma linha `Uncategorized` para
      provar que o dashboard a renderiza sem tropeçar.

## 8. Verificação e fechamento

**Barreira de entrada:** grupo 7 concluído.

**Barreira de saída:** a mudança está verificada por execução, não por leitura — nenhum item deste
grupo é dado por feito sem a saída do comando lida.

- [x] 8.1 Rodar `./gradlew jvmTest` e ler a saída inteira; rodar `./gradlew :app:android:assembleDebug`.
      Ambos verdes.
- [ ] 8.2 **Pendente — exige interação com a GUI.** `./gradlew :app:desktop:run` sobe e roda sem
      exceção (90s observados, log só com avisos de SLF4J), mas o passeio visual em si não foi feito:
      criar uma despesa e uma receita sem categoria no mês e conferir as duas linhas, mais a
      exportação HTML. Exercitar o caminho no app (`./gradlew :app:desktop:run`): um mês com despesa e receita sem
      categoria mostra as duas linhas, cada uma no seu detalhamento, por último e visualmente distinta;
      um mês inteiramente classificado mostra exatamente o que mostrava antes. Conferir também a
      exportação HTML do relatório, que monta a sua própria lista.
- [x] 8.3 Conferir os dois arquivos de `strings.xml` uma última vez (a chave existe em pt e en) e
      confirmar que nenhuma migração de banco foi criada — nada aqui é persistido, todo número é
      derivado das entries.
