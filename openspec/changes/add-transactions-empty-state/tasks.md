## 1. Estado

- [x] 1.1 Em `TransactionsUiState.kt`, declarar o `sealed interface ListState` com
  `Loading`, `EmptyLedger`, `EmptyScope(canClearFilters)` e `Content(transactions)` (D1),
  documentando por que o mapa mora dentro de `Content`.
- [x] 1.2 Substituir o campo `transactions: Map<LocalDate, List<Transaction>>` por
  `listState: ListState = ListState.Loading` em `TransactionsUiState`.
- [x] 1.3 Adicionar `data object ClearFilters : TransactionsAction()` em
  `TransactionsAction.kt`.

## 2. ViewModel

- [x] 2.1 Em `TransactionsViewModel`, extrair a lista recortada para uma val local e derivar
  `listState` pelo `when` do D2: `Content` se houver itens visíveis, `EmptyLedger` se a lista
  pré-filtro estiver vazia, `EmptyScope` caso contrário.
- [x] 2.2 Calcular `canClearFilters` a partir dos filtros **efetivos** — `target` e
  `installmentOnly` já neutralizados pelo escopo, mais `category`, `label` e `recurringOnly`
  (D3).
- [x] 2.3 Tratar `TransactionsAction.ClearFilters` em `onAction` com
  `filters.value = TransactionsFilters()`, sem tocar `selectedYearMonth` nem `selectedScope`
  (D3/D4).
- [x] 2.4 Confirmar que `initialValue = TransactionsUiState()` agora significa `Loading` e que
  nenhuma emissão posterior volta a esse estado (D6).

## 3. Strings

- [x] 3.1 Adicionar em `core/resources/.../values/strings.xml`:
  `transactions_empty_title`, `transactions_empty_body`, `transactions_empty_filter_title`,
  `transactions_empty_filter_body`, `transactions_empty_filter_action` (D7).
- [x] 3.2 Adicionar as mesmas chaves em `values-en/strings.xml`.

## 4. UI

- [x] 4.1 Criar o `private fun TransactionsEmptyState(...)` em `TransactionsScreen.kt`, no
  padrão visual das demais telas (Column centralizada, título 20.sp SemiBold, corpo
  `bodyMedium`/`onSurfaceVariant`), com o botão de limpar filtros apenas quando
  `canClearFilters` (spec: vazio de origem não oferece a ação).
- [x] 4.2 Trocar o `uiState.transactions.forEach` por um `when (uiState.listState)`:
  `Content` mantém os cabeçalhos de data e os `TransactionCard`; `EmptyLedger` e `EmptyScope`
  emitem o `item("empty_state")` com `animateItem()`; `Loading` não emite nada (D5).
- [x] 4.3 Verificar que `SummaryCard` e `FiltersRow` continuam emitidos em todos os estados
  (spec: controles do recorte permanecem visíveis).
- [x] 4.4 Ajuste visual após a conferência na tela: o empty state passa a ser centralizado
  num `Box(fillParentMaxWidth)` com `widthIn(max = 400.dp)` e padding de 24.dp — em desktop e
  tablet o texto corrido na largura toda lia como parágrafo, não como aviso curto. Ícone de
  48.dp em `onSurfaceVariant` acima do título, distinto por estado
  (`AutoMirrored.Outlined.ReceiptLong` para o vazio de origem, `Outlined.FilterAltOff` para o
  de recorte), acompanhando a distinção que o texto já faz.

## 5. Testes

- [x] 5.1 Em `feature/transactions/impl/src/commonTest`, criar/estender o teste do ViewModel
  cobrindo: estado inicial é `Loading`; repositório vazio → `EmptyLedger`; transações só em
  outro mês → `EmptyScope`; filtro que corta tudo → `EmptyScope(canClearFilters = true)`; mês
  sem lançamentos e filtros neutros → `EmptyScope(canClearFilters = false)`.
- [x] 5.2 Testar que `ClearFilters` devolve os filtros ao neutro preservando mês, escopo e
  `balanceOverview`.
- [x] 5.3 Testar que um filtro neutralizado pelo escopo (alvo fora do escopo geral, parcelados
  sob o escopo de contas) não faz `canClearFilters` virar `true`.
- [x] 5.4 Ajustar os testes existentes que leem `uiState.transactions` para o novo
  `listState`.

## 6. Verificação

- [x] 6.1 `./gradlew :app:shared:testDebugUnitTest` verde.
- [x] 6.2 `./gradlew :app:android:assembleDebug` compilando.
- [x] 6.3 Conferir na tela: abertura sem piscar vazio, mês futuro vazio com mensagem de
  recorte, filtro que corta tudo com botão de limpar funcionando. Conferido manualmente; o
  que a conferência pediu está em 4.4.
