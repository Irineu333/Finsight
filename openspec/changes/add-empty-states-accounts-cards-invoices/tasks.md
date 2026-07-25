## 1. Componente compartilhado

- [ ] 1.1 Criar `EmptyStateMessage` em `:core:designsystem`
  (`ui/component/EmptyStateMessage.kt`): ícone 48.dp em `onSurfaceVariant`, título 20.sp
  SemiBold, corpo `bodyMedium`/`onSurfaceVariant`, ambos centralizados, e um slot opcional de
  ação abaixo (D5).
- [ ] 1.2 Trocar o corpo do `TransactionsEmptyState` privado em `TransactionsScreen.kt` por uma
  chamada ao novo componente, sem mudança visual — a escolha de ícone, textos e do botão de
  limpar filtros continua na tela (D5).

## 2. Strings

- [ ] 2.1 Adicionar em `core/resources/.../values/strings.xml` as seis famílias do D9:
  `accounts_empty_title`/`_body`, `accounts_empty_filter_title`/`_body`,
  `credit_cards_transactions_empty_title`/`_body`,
  `credit_cards_transactions_empty_filter_title`/`_body`,
  `invoice_transactions_empty_title`/`_body`,
  `invoice_transactions_empty_filter_title`/`_body`.
- [ ] 2.2 Adicionar as mesmas chaves em `values-en/strings.xml`.
- [ ] 2.3 Confirmar que nenhuma tela cria chave própria para "Limpar filtros" — as três
  reaproveitam `transactions_empty_filter_action` (D9).

## 3. Contas

- [ ] 3.1 Em `AccountsUiState`, declarar o `sealed interface ListState` com `EmptyAccount`,
  `EmptyScope(canClearFilters)` e `Content(transactions)`, sem `Loading` — a UiState já é
  `Loading | Content` (D1) — documentando por que o mapa mora dentro de `Content`.
- [ ] 3.2 Substituir o campo `transactions` de `AccountsUiState.Content` por
  `listState: ListState`.
- [ ] 3.3 Adicionar `data object ClearFilters : AccountsAction()`.
- [ ] 3.4 Em `AccountsViewModel`, derivar `listState` pelo `when` do D2, usando
  `selectedAccountTransactions` (todos os meses) como recorte-raiz e `filteredTransactions`
  como lista visível.
- [ ] 3.5 Calcular `canClearFilters` a partir de categoria, tipo e recorrentes — o mês fica de
  fora (D3).
- [ ] 3.6 Tratar `AccountsAction.ClearFilters` em `onAction` devolvendo os filtros ao neutro,
  sem tocar `selectedMonth` nem `selectedAccountId` (D3).
- [ ] 3.7 Em `AccountsScreen`, trocar `uiState.transactions.forEach` por um `when` sobre
  `listState`: `Content` mantém cabeçalhos de data e `TransactionCard`; os dois vazios emitem
  `item("empty_state")` com `EmptyStateMessage` e `animateItem()` (D8), com
  `AutoMirrored.Outlined.ReceiptLong` no de origem e `Outlined.FilterAltOff` no de recorte
  (D6), e botão de limpar filtros apenas quando `canClearFilters`.
- [ ] 3.8 Verificar que pager, `AccountActions` e `FiltersRow` continuam emitidos nos dois
  estados vazios.

## 4. Cartões

- [ ] 4.1 Em `CreditCardsUiState`, declarar o `ListState` com `EmptyInvoice`,
  `EmptyScope(canClearFilters)` e `Content(transactions)` e substituir o campo `transactions`
  de `Content` (D1).
- [ ] 4.2 Adicionar `data object ClearFilters : CreditCardsAction()`.
- [ ] 4.3 Em `CreditCardsViewModel`, derivar `listState` pelo `when` do D2 usando o resultado
  de `transactionsFlow` (lançamentos da fatura corrente do cartão selecionado) como
  recorte-raiz; manter o `return@combine CreditCardsUiState.Empty` de "nenhum cartão" como
  está.
- [ ] 4.4 Calcular `canClearFilters` a partir de categoria, tipo, recorrentes e parcelados
  (D3), e tratar `ClearFilters` sem tocar `selectedCardId`.
- [ ] 4.5 Em `CreditCardsScreen`, trocar `uiState.transactions.forEach` pelo `when` sobre
  `listState`, com `Outlined.CreditCard` no vazio de origem e `Outlined.FilterAltOff` no de
  recorte (D6/D8).

## 5. Faturas

- [ ] 5.1 Em `InvoiceTransactionsUiState`, declarar o `ListState` com os **quatro** casos —
  `Loading`, `EmptyInvoice`, `EmptyScope(canClearFilters)`, `Content(transactions)` — e
  substituir o campo `transactions` por `listState: ListState = ListState.Loading` (D1/D4).
- [ ] 5.2 Adicionar `data object ClearFilters : InvoiceTransactionsAction()`.
- [ ] 5.3 Em `InvoiceTransactionsViewModel`, derivar `listState` pelo `when` do D2 usando
  `invoiceTransactions` (pré-filtro, da fatura selecionada) como recorte-raiz.
- [ ] 5.4 Calcular `canClearFilters` (categoria, tipo, recorrentes, parcelados) e tratar
  `ClearFilters` sem tocar `selectedInvoiceIndex` (D3).
- [ ] 5.5 Confirmar que `initialValue` agora significa `Loading` e que nenhuma emissão
  posterior — troca de fatura ou de filtro — volta a esse estado (D4).
- [ ] 5.6 Em `InvoiceTransactionsScreen`, trocar `uiState.transactions.forEach` pelo `when`
  sobre `listState`: `Loading` não emite item algum; os dois vazios emitem o
  `EmptyStateMessage` (D8), com `Outlined.CreditCard` e `Outlined.FilterAltOff` (D6).

## 6. Testes

- [ ] 6.1 Em `feature/accounts/impl/src/commonTest`, testar o `AccountsViewModel`: conta sem
  lançamento algum → `EmptyAccount`; conta com lançamentos só em outro mês → `EmptyScope`
  com `canClearFilters = false`; filtro que corta tudo → `EmptyScope(canClearFilters = true)`;
  mês com lançamentos → `Content`.
- [ ] 6.2 Testar que `AccountsAction.ClearFilters` devolve os filtros ao neutro preservando mês
  e conta selecionada.
- [ ] 6.3 Em `feature/creditcards/impl/src/commonTest`, testar o `CreditCardsViewModel`: fatura
  corrente sem lançamentos → `EmptyInvoice`; filtro que corta tudo →
  `EmptyScope(canClearFilters = true)`; `ClearFilters` preservando o cartão selecionado; e que
  "nenhum cartão cadastrado" continua emitindo `CreditCardsUiState.Empty`.
- [ ] 6.4 Testar o `InvoiceTransactionsViewModel`: estado inicial é `Loading`; fatura sem
  lançamentos → `EmptyInvoice`; filtro que corta tudo → `EmptyScope(canClearFilters = true)`;
  troca de fatura não volta a `Loading`.
- [ ] 6.5 Ajustar os testes existentes que leem `uiState.transactions` nas três telas
  (incluindo `InvoiceTransactionsViewModelCharacterizationTest`) para o novo `listState`.

## 7. Verificação

- [ ] 7.1 `./gradlew :app:shared:testDebugUnitTest` verde.
- [ ] 7.2 `./gradlew :app:android:assembleDebug` compilando.
- [ ] 7.3 Conferir nas telas: conta nova sem lançamentos, mês vazio de conta que movimenta,
  cartão com fatura recém-aberta, fatura antiga sem lançamentos, filtro que corta tudo com o
  botão de limpar funcionando, e abertura da tela de faturas sem piscar vazio.
