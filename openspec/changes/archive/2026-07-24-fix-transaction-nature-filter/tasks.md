> Ordem: primeiro os recursos que não dependem de nada (cor, strings), depois a fronteira que quebra em compilação (rota/NavType → grafo → dashboard), depois o filtro em si, depois o dropdown, e testes por último.
>
> Nada em `core/ledger` muda: `TransactionLabel` e `Transaction.label` já existem e já são o dono da natureza. Sem migração, sem mudança de escrita, sem mudança em nenhuma figura do razão.
>
> A tela de faturas (`InvoiceTransactionsViewModel.kt:254-263`) **não** é tocada — ver design D3: ela usa `TransactionType` corretamente, sobre a perna do próprio cartão.

## 1. Cor da transferência (D5)

- [x] 1.1 `core/designsystem` `ui/theme/theme/Color.kt`: adicionar `val Transfer = Color(0xFF3B82F6) // Blue - Transferências` na seção "Income/Expense colors" (após `InvoicePayment`, `:18`). Constante **própria** — não reusar `Info` (`:39`) nem `CategoryColor` (`:49`), que hoje têm o mesmo valor com outra semântica.

## 2. Strings do dropdown

- [x] 2.1 `core/resources` `values/strings.xml` (após `:162`): `transactions_filter_type_transfer` = "Transferência", `transactions_filter_type_payment` = "Pagamento".
- [x] 2.2 `core/resources` `values-en/strings.xml` (após `:161`): "Transfer", "Payment".

## 3. Rota e navegação (D4)

- [x] 3.1 `feature/transactions/api`: criar `TransactionLabelNavType : NavType<TransactionLabel?>(isNullableAllowed = true)`, cópia literal de `TransactionTypeNavType` trocando o enum (`put` grava `value.name`, `get` lê com `TransactionLabel::valueOf`, `parseValue` trata `"null"`).
- [x] 3.2 `TransactionsRoute`: `filterType: TransactionType? = null` → `filterLabel: TransactionLabel? = null`. Trocar o import de `TransactionType` por `TransactionLabel`.
- [x] 3.3 **Apagar** `TransactionTypeNavType.kt` — o grafo desta feature era o único consumidor (confirmado por grep).
- [x] 3.4 `TransactionsGraph.kt`: no `typeMap`, `typeOf<TransactionLabel?>() to TransactionLabelNavType()`; passar `categoryLabel = route.filterLabel` para a tela; ajustar os dois imports.
- [x] 3.5 `feature/dashboard/impl` `DashboardComponentContent.kt`: `openTransactions` muda para `(TransactionLabel?, TransactionTarget?)` (`:92`), e a assinatura correspondente em `DashboardConcreteBalanceSection`/`DashboardRecentsSection` (`:243` e a irmã). Os 2 call sites com valor viram `TransactionLabel.INCOME` (`:369`) e `TransactionLabel.EXPENSE` (`:380`); `openTransactions(null, null)` (`:260`) não muda. Trocar o import de `TransactionType` por `TransactionLabel` — se `TransactionType` deixar de ser usado no arquivo, remover.

## 4. O filtro (D1)

- [x] 4.1 `TransactionsFilters.kt`: `val type: TransactionType?` → `val label: TransactionLabel?`.
- [x] 4.2 `TransactionsAction.kt:15`: `data class SelectType(val type: TransactionType?)` → `data class SelectLabel(val label: TransactionLabel?)`.
- [x] 4.3 `TransactionsUiState.kt:27`: `selectedType: TransactionType?` → `selectedLabel: TransactionLabel?`.
- [x] 4.4 `TransactionsViewModel.kt`: parâmetro `filterType` → `filterLabel: TransactionLabel?`; `filters` inicial e `selectedType = filters.type` (`:87`) acompanham; branch `SelectType` → `SelectLabel` (`:125`).
- [x] 4.5 `TransactionsViewModel.kt:159-164`: trocar o filtro inteiro por
      `private fun List<Transaction>.filter(label: TransactionLabel?) = if (label == null) this else filter { it.label == label }`.
      Remover o import de `deriveTransactionType` (`:9`) — nenhum outro uso no arquivo. **Não** tocar `filter(target)` (`:166-169`) nem `primaryEntry`.
- [x] 4.6 `TransactionsModule.kt:59`: `filterType = getOrNull()` → `filterLabel = getOrNull()`. Resolução por **tipo** em runtime — ver risco em design: um rename pela metade deixa o filtro chegar `null` em silêncio.

## 5. Dropdown de 5 opções (D5)

- [x] 5.1 `TransactionsScreen.kt:44-49`: parâmetro `categoryType: TransactionType?` → `categoryLabel: TransactionLabel?`; `parametersOf(categoryLabel, null, target)`.
- [x] 5.2 `TypeFilterChip` (`:288-361`): assinatura passa a `selectedLabel: TransactionLabel?`; `chipColor` e o texto do chip ganham os 5 branches; o `forEach` passa a iterar `TransactionLabel.entries`; `onClick` emite `SelectLabel`. Os `when` deixam de precisar de `else` (o enum é fechado em 5).
- [x] 5.3 `TransactionsScreen.kt:39-41`: acrescentar os aliases de import `Transfer as TransferColor` e `InvoicePayment as PaymentColor`, seguindo o padrão já usado para `Adjustment`/`Expense`/`Income`.
- [x] 5.4 `FiltersRow` (`:189`): `selectedType = uiState.selectedType` → `selectedLabel = uiState.selectedLabel`.
- [x] 5.5 (D7) `TransactionsScreen.kt:143-151`: trocar `when (transactionUi.direction) { TransactionType.ADJUSTMENT -> ... }` por `if (transactionUi.label == TransactionLabel.ADJUSTMENT)`. Equivalente hoje; deixa a tela com um só vocabulário. Se `TransactionType` deixar de ser usado no arquivo, remover o import.

## 6. Testes

- [x] 6.1 `TransactionsViewModelCharacterizationTest.kt:86`: `filterType = null` → `filterLabel = null`. Nada mais — ele caracteriza o **resumo do mês**, não o filtro.
- [x] 6.2 Novo teste do eixo, no mesmo pacote, reusando as contas/`op()` do teste existente: uma transação de cada forma (despesa, receita, transferência entre duas `ASSET`, pagamento `ASSET`+`LIABILITY`, ajuste com `EQUITY`) e, para cada natureza, `SelectLabel(X)` devolve **exatamente** a sua.
- [x] 6.3 Teste de **partição** (o requisito): a união dos resultados das 5 opções é igual à lista sem filtro, sem repetição e sem ausência.
- [x] 6.4 Teste dos dois casos que motivaram o change, explícitos: filtrar por `EXPENSE` **não** devolve a transferência nem o pagamento.
- [x] 6.5 Teste de **paridade com o resumo**: com o fake de `IEntryRepository` reportando `assetMonthFlows.expense` e `liabilityMonthFlows.payment`, a soma da lista filtrada por cada natureza bate com a linha correspondente do `balanceOverview`.
- [x] 6.6 Teste do parâmetro de rota: construir o ViewModel com `filterLabel = TransactionLabel.PAYMENT` e verificar que a lista já abre filtrada (cobre o risco de resolução por tipo do Koin).

## 7. Validação

- [x] 7.1 `./gradlew :app:shared:testDebugUnitTest` (o rename atravessa `transactions` e `dashboard`; ambos precisam compilar).
- [x] 7.2 `./gradlew allTests`. — falha só no link do binário de teste iOS (`ld: framework FirebaseCore not found`, `iosApp/Pods` ausente no ambiente); validado via `jvmTest` + `:app:shared:testDebugUnitTest`.
- [x] 7.3 Conferência manual na tela: sem filtro lista as 5 naturezas; "Despesa" não traz transferência nem pagamento; as 5 cores aparecem no chip; o roxo do chip "Pagamento" é o mesmo da linha "Pagamento" do `SummaryCard`.
- [x] 7.4 `openspec archive fix-transaction-nature-filter`.
