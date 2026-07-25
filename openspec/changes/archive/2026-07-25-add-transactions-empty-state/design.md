## Context

`TransactionsScreen` monta uma única `LazyColumn` com o `SummaryCard`, a `FiltersRow` e, em
seguida, `uiState.transactions.forEach { ... }`. Quando o mapa está vazio o `forEach` não
emite nada e a tela termina nos chips — branco, sem explicação.

`TransactionsUiState` é uma `data class` com valores-padrão, e `TransactionsViewModel` a usa
como `initialValue` do `stateIn`. O estado "ainda não li o banco" é portanto **idêntico** ao
estado "li e não há nada": mesmo mapa vazio, mesmo `BalanceOverview.Overall()` zerado. É a
única feature de lista do app sem estado vazio, e a única cuja UiState não é `sealed` — todas
as outras (`BudgetsUiState`, `RecurringUiState`, `CreditCardsUiState`, `CategoriesUiState`)
usam `Loading | Empty | Content`.

Restrição que separa esta tela das demais: aqui o vazio **não pode** tomar a tela inteira. O
resumo e os chips não são decoração — são os controles que produzem o recorte e, portanto, a
única saída do vazio. Um `sealed TransactionsUiState` no molde das outras features obrigaria a
duplicar esse chrome no ramo vazio ou a perdê-lo.

## Goals / Non-Goals

**Goals:**

- Dar nome ao vazio da tela de transações, distinguindo vazio de origem de vazio de recorte.
- Tornar o carregamento inicial indistinguível de vazio **impossível por construção**, não por
  disciplina de quem preenche a UiState.
- Oferecer a saída do vazio de recorte (limpar filtros) só quando ela existe.
- Manter o resumo e os chips visíveis no vazio.

**Non-Goals:**

- Extrair um componente compartilhado de empty state. Seis features já duplicam o padrão
  (`EmptyBudgetsState`, `RecurringEmptyState`, `EmptyCreditCardsState`, `EmptyFilterState`, …);
  unificá-las é uma mudança própria, com seu próprio recorte de estilo, e arrastá-la para cá
  misturaria dois porquês.
- Estado de erro. O `combine` atual não tem caminho de falha exposto; inventar um aqui seria
  especular.
- Mexer em `transaction-scope`: nenhum recorte muda, nenhuma linha do resumo muda.

## Decisions

### D1 — O estado da lista é um `sealed interface` **dentro** da UiState, não a própria UiState

`TransactionsUiState` continua sendo a `data class` que descreve o chrome (resumo, escopo, mês,
chips) e ganha **um** campo:

```kotlin
sealed interface ListState {
    data object Loading : ListState
    data object EmptyLedger : ListState
    data class EmptyScope(val canClearFilters: Boolean) : ListState
    data class Content(val transactions: Map<LocalDate, List<Transaction>>) : ListState
}

val listState: ListState = ListState.Loading
```

As transações saem do topo da UiState e passam a morar dentro de `Content` — é o que faz a
ambiguidade sumir por construção: não existe mais um mapa vazio à espera de interpretação, e
`initialValue = TransactionsUiState()` já significa `Loading` sem que ninguém precise lembrar
de marcar um booleano.

*Alternativa considerada:* `isLoading: Boolean` + `hasAnyTransaction: Boolean` ao lado do mapa.
Rejeitada: três campos independentes descrevendo um estado com quatro valores válidos admitem
combinações sem sentido (`isLoading = true` com mapa cheio), e a tela teria de reconstruir a
decisão a cada recomposição.

*Alternativa considerada:* tornar `TransactionsUiState` inteira `sealed`, como nas outras
features. Rejeitada pela restrição do Context — o chrome sobrevive ao vazio e teria de ser
repetido em cada ramo.

### D2 — A distinção entre os dois vazios deriva da lista pré-filtro, no ViewModel

O `combine` já recebe `transactions` — todas, de todos os meses — antes de qualquer filtro. A
regra é uma linha, e fica onde a informação está:

```kotlin
val visible = transactions.filter(...)./* … */.groupBy { it.date }

listState = when {
    visible.isNotEmpty() -> ListState.Content(visible)
    transactions.isEmpty() -> ListState.EmptyLedger
    else -> ListState.EmptyScope(canClearFilters = /* ver D3 */)
}
```

A tela não recebe insumos para decidir; recebe a decisão. É a mesma divisão de trabalho do
resto do arquivo, onde `mustShowTargetFilter` e o `target` neutralizado pelo escopo já são
resolvidos antes da UiState.

*Alternativa considerada:* deduzir vazio de origem de "nenhum filtro ativo e lista vazia".
Rejeitada — é falso: no mês que vem, sem filtro algum, a tela mentiria dizendo que o usuário
nunca registrou nada.

### D3 — `canClearFilters` olha os filtros **efetivos**, não os armazenados

O ViewModel já neutraliza dois filtros conforme o escopo: `target` fora do escopo geral e
`installmentOnly` sob o escopo de contas. Um filtro neutralizado não aparece na `FiltersRow` e
não recorta nada — oferecê-lo para limpar seria um botão que promete mudar a lista e não muda.
Então `canClearFilters` deriva dos mesmos valores efetivos já calculados (`target`,
`installmentOnly`) somados a `category`, `label` e `recurringOnly`.

Mês e escopo ficam de fora da ação: eles governam também o resumo (`transaction-scope`), e uma
ação anunciada como "limpar filtros" que reescreve os números do topo faz mais do que diz.

### D4 — Limpar filtros descarta também os filtros vindos da rota

`TransactionsAction.ClearFilters` faz `filters.value = TransactionsFilters()`, o que apaga
inclusive `filterLabel` / `category` / `filterTarget` recebidos por parâmetro de navegação.
Isso é deliberado: uma vez na tela, esses valores são chips como os outros — já editáveis pelo
usuário — e preservá-los tornaria a ação inexplicável ("limpei e continua filtrado").

### D5 — O vazio é um `item` da própria `LazyColumn`

```kotlin
item(key = "empty_state") { TransactionsEmptyState(state = listState, onAction = onAction, …) }
```

Nada de `Scaffold` com ramos ou de segunda árvore de composição: o vazio ocupa exatamente o
espaço da lista, herda o `contentPadding` e o `animateItem()` que os demais itens já usam, e a
transição vazio ↔ lista é a mesma animação de item de sempre. `Loading` não emite item algum —
a tela simplesmente ainda não afirma nada.

O composable fica `private` em `TransactionsScreen.kt`, seguindo o que as outras cinco telas
fazem hoje (ver Non-Goals).

### D6 — `Loading` é um estado de partida, não recorrente

`Loading` só existe como `initialValue`; toda emissão do `combine` já traz `Content`,
`EmptyLedger` ou `EmptyScope`. Trocar de mês, de escopo ou de filtro **não** volta para
`Loading` — o recorte é feito em memória sobre a lista já observada, e piscar um vazio
indeterminado a cada toque num chip seria pior que o branco de hoje.

### D7 — Strings

Novas chaves em `core/resources` (`values` e `values-en`):

| chave | uso |
|---|---|
| `transactions_empty_title` / `transactions_empty_body` | vazio de origem, apontando o botão de adicionar do chrome |
| `transactions_empty_filter_title` / `transactions_empty_filter_body` | vazio de recorte |
| `transactions_empty_filter_action` | "Limpar filtros" |

O vazio de origem **não** ganha botão próprio: o FAB de adicionar transação já está na tela,
vindo do chrome da home. Dois comandos idênticos a um toque de distância competiriam entre si.

## Risks / Trade-offs

- **`ListState.Content` poderia carregar um mapa vazio** se alguém o construísse fora do `when`
  do D2 → o `when` é o único ponto de construção, dentro do mesmo bloco do `combine`; um teste
  do ViewModel cobre as três saídas.
- **`transactions` deixa de ser um campo de topo da UiState** → é uma quebra local, contida em
  `TransactionsScreen` e nos testes do módulo `impl`; nada em `feature/transactions/api` expõe
  a UiState.
- **`ClearFilters` apagar o filtro da rota pode surpreender** quem chegou por "ver transações
  desta categoria" → mitigado por D3: o botão só aparece quando há filtro ativo, e o chip
  visível diz qual é antes de o usuário tocá-lo.
- **Uma lista longa mostrando o vazio no meio da rolagem** não ocorre — vazio e itens são
  mutuamente exclusivos por construção do `ListState`.
