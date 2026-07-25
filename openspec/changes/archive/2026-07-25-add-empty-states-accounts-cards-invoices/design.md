## Context

As três telas seguem o mesmo desenho: uma `LazyColumn` com um pager no topo (contas, cartões,
faturas), uma linha de ações, a `FiltersRow` e, em seguida, `uiState.transactions.forEach`.
Com o mapa vazio o `forEach` não emite nada e a tela termina nos chips.

O que difere entre elas é o **recorte-raiz** — o conjunto que existe antes de qualquer filtro,
e cuja ausência é o que separa "não há nada" de "seu recorte não pegou nada":

| tela | recorte-raiz | recortes acima dos filtros |
|---|---|---|
| contas | lançamentos da conta selecionada (todos os meses) | mês |
| cartões | lançamentos da fatura aberta do cartão selecionado | — |
| faturas | lançamentos da fatura selecionada no pager | — |

O estado de carregamento também difere: `AccountsUiState` e `CreditCardsUiState` já são
`sealed` com um ramo `Loading`; `InvoiceTransactionsUiState` é uma `data class` cujo
`initialValue` (`InvoiceTransactionsUiState()`) tem `invoices` e `transactions` vazios — o
mesmo formato de "li e a fatura está vazia". É o mesmo defeito que a tela de transações tinha.

A tela de transações já resolveu esse problema (`transactions-empty-state`) com um
`sealed interface ListState` dentro da UiState e um composable privado de mensagem. Este
change repete a **forma** e, agora que ela vai existir em quatro telas, extrai o **layout**.

Restrição comum às três: o vazio não pode tomar a tela inteira. O pager e os chips são os
controles que produzem o recorte — e a única saída dele. (A tela de cartões já tem um vazio de
tela inteira, `CreditCardsUiState.Empty`, mas para outra coisa: *nenhum cartão cadastrado*.
Esse continua como está.)

## Goals / Non-Goals

**Goals:**

- Dar nome ao vazio nas três listas, distinguindo vazio de origem de vazio de recorte.
- Tornar impossível por construção, na tela de faturas, confundir carregamento com vazio.
- Oferecer a saída do vazio de recorte (limpar filtros) só quando ela existe.
- Manter pager, ações e chips visíveis no vazio.
- Ter **um** layout de mensagem de vazio, não mais quatro cópias.

**Non-Goals:**

- Estado vazio para *nenhuma conta cadastrada*: o domínio garante ao menos uma
  (`EnsureDefaultAccountUseCase` cria a padrão, e a padrão não pode ser arquivada nem
  excluída — `CANNOT_ARCHIVE_DEFAULT`). Inventar uma tela para um estado que o domínio não
  produz seria especular.
- Estado vazio para *cartão sem nenhuma fatura*: `AddCreditCardUseCase` abre a fatura corrente
  na criação e `DeleteFutureInvoiceUseCase` só remove faturas futuras — a lista nunca fica
  vazia.
- Migrar para o componente compartilhado os outros cinco vazios já existentes
  (`EmptyBudgetsState`, `RecurringEmptyState`, `EmptyCreditCardsState`, categorias, contas e
  cartões arquivados): eles têm layouts próprios (botão de ação primária, texto único) e
  uniformizá-los é uma mudança de estilo com seu próprio porquê. Só a tela de transações
  migra, porque o componente **é** o layout dela, extraído.
- Estado de erro. Nenhum dos `combine` expõe caminho de falha; inventar um aqui seria
  especular.
- Mexer em recorte: nenhum filtro, mês, escopo ou seleção de fatura muda de comportamento.

## Decisions

### D1 — Cada tela ganha o seu `sealed interface ListState`, dentro da UiState

Repete-se a forma de `TransactionsUiState.ListState`: o estado da lista é um campo da UiState,
e as transações moram **dentro** de `Content`.

```kotlin
// AccountsUiState.Content
sealed interface ListState {
    data object Loading : ListState                                  // só em contas/faturas†
    data object EmptyAccount : ListState                             // a conta nunca movimentou
    data class EmptyScope(val canClearFilters: Boolean) : ListState  // mês/filtros cortaram tudo
    data class Content(val transactions: Map<LocalDate, List<TransactionUi>>) : ListState
}
```

† Em contas e cartões o `Loading` da lista é desnecessário: a UiState inteira já é
`Loading | Content`, e `Content` só existe depois da primeira emissão. Lá o `ListState` tem
três casos. Na tela de faturas, que não tem esse ramo, o `ListState` tem os quatro — é ele que
carrega a fase de carregamento (D4).

Os nomes do caso de origem seguem o que está vazio em cada tela: `EmptyAccount` (contas),
`EmptyInvoice` (cartões e faturas). Não são o mesmo tipo em módulos diferentes; são a mesma
forma aplicada a três domínios distintos.

*Alternativa considerada:* um `ListState` genérico em `:core:ui`, compartilhado pelas quatro
telas. Rejeitada: os casos de origem não são os mesmos (`EmptyLedger`, `EmptyAccount`,
`EmptyInvoice` afirmam coisas diferentes), e o que sobraria de comum — "vazio com ou sem
filtros" — é pequeno demais para justificar um tipo que amarra quatro features a uma evolução
conjunta. O que se repete é o **layout**, e é ele que se extrai (D5).

### D2 — A distinção deriva do recorte-raiz, no ViewModel

Em cada `combine`, o conjunto pré-filtro já está em mãos; a regra é uma linha, e fica onde a
informação está:

```kotlin
listState = when {
    visible.isNotEmpty() -> ListState.Content(visible)
    root.isEmpty()       -> ListState.EmptyAccount     // ou EmptyInvoice
    else                 -> ListState.EmptyScope(canClearFilters = …)
}
```

`root` é, por tela: `selectedAccountTransactions` (contas — todos os meses, para que um mês
sem lançamentos numa conta que movimenta continue sendo recorte, não origem); o resultado de
`transactionsFlow` (cartões); `invoiceTransactions` (faturas). A tela recebe a decisão, não os
insumos para tomá-la.

### D3 — `canClearFilters` olha só os filtros de lista, e `ClearFilters` só os devolve ao neutro

Por tela: contas — categoria, tipo, recorrentes; cartões e faturas — categoria, tipo,
recorrentes, parcelados. O mês (contas), a conta, o cartão e a fatura selecionados ficam de
fora: eles governam também os números do cartão do topo, e uma ação anunciada como "limpar
filtros" que reescreve o resumo faz mais do que diz. É a mesma fronteira que
`transactions-empty-state` traçou entre filtros e escopo.

Com todos os filtros no neutro, `canClearFilters` é `false` e o botão não aparece — ele
prometeria uma mudança que não pode entregar. Na tela de contas é exatamente o caso do mês sem
lançamentos.

### D4 — A tela de faturas ganha a fase de carregamento pelo `ListState`, não por um `sealed` na UiState

`InvoiceTransactionsUiState` continua `data class`: ela descreve o cartão, as faturas e os
chips, e um `sealed` no topo obrigaria a repetir tudo isso em cada ramo — a mesma razão que
levou a tela de transações a não ser `sealed`. `listState: ListState = ListState.Loading` no
`initialValue` já significa "ainda não li", sem ninguém precisar lembrar de um booleano.

`Loading` é estado de partida, não recorrente: trocar de fatura ou de filtro recorta em
memória sobre o que já foi observado e não volta a `Loading` — piscar um vazio indeterminado a
cada toque seria pior que o branco de hoje.

### D5 — O layout do vazio vira um componente em `:core:designsystem`

Ícone, título, corpo e ação opcional, centralizados, com largura máxima — o que hoje é
`TransactionsEmptyState`:

```kotlin
@Composable
fun EmptyStateMessage(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
)
```

Cabe em `:core:designsystem` (não em `:core:ui`) porque não renderiza nenhum modelo do
domínio: recebe texto e ícone prontos. Quem decide **qual** ícone e **qual** texto continua
sendo cada tela — a decisão de negócio (que vazio é este) fica na feature; só a forma é
compartilhada.

*Alternativa considerada:* deixar as três telas com cópias privadas, como as outras cinco
features fazem. Rejeitada: seriam três cópias novas de um layout que já existe idêntico,
criadas de uma vez e no mesmo change — é duplicação deliberada, não convergência histórica.

### D6 — Ícones distinguem as duas leituras, como na tela de transações

`Outlined.FilterAltOff` para o vazio de recorte, em todas as telas. Para o de origem, o objeto
que está vazio: `AutoMirrored.Outlined.ReceiptLong` (contas, lançamentos da conta) e
`Outlined.CreditCard` (cartões e faturas, lançamentos da fatura).

### D7 — O vazio de origem descreve; não manda tocar botão nenhum

Nenhuma das três telas oferece "adicionar lançamento": o FAB de contas cria conta, o de cartões
cria cartão, e a de faturas não tem FAB. Então a mensagem de origem constata o estado — "esta
conta ainda não tem lançamentos", "esta fatura está sem lançamentos" — e para por aí. Só o
vazio de recorte ganha ação, e só quando há filtro a limpar (D3).

### D8 — O vazio é um `item` da própria `LazyColumn`

`item(key = "empty_state") { … }`, herdando `contentPadding` e `animateItem()` dos demais
itens, como na tela de transações. Nada de segunda árvore de composição, e o pager, as ações e
os chips continuam emitidos em todos os estados. `Loading` (faturas) não emite item algum.

### D9 — Strings

Novas chaves em `core/resources` (`values` e `values-en`), uma família por tela:

| chave | uso |
|---|---|
| `accounts_empty_title` / `accounts_empty_body` | conta sem lançamento algum |
| `accounts_empty_filter_title` / `accounts_empty_filter_body` | mês/filtros cortaram tudo |
| `credit_cards_transactions_empty_title` / `_body` | fatura aberta do cartão sem lançamentos |
| `credit_cards_transactions_empty_filter_title` / `_body` | filtros cortaram tudo |
| `invoice_transactions_empty_title` / `_body` | fatura selecionada sem lançamentos |
| `invoice_transactions_empty_filter_title` / `_body` | filtros cortaram tudo |

A ação reaproveita `transactions_empty_filter_action` ("Limpar filtros"): é literalmente o
mesmo comando com o mesmo efeito; duplicar a chave só criaria duas traduções para uma palavra.
`credit_cards_empty` (nenhum cartão cadastrado) continua existindo e não se confunde com as
novas.

## Risks / Trade-offs

- **Quatro `ListState` parecidos em três módulos** → é repetição de forma, não de regra: cada
  um nomeia um vazio diferente. O que era duplicação real — o layout — foi extraído (D5).
- **`Content` poderia carregar um mapa vazio** se construído fora do `when` do D2 → o `when` é
  o único ponto de construção em cada `combine`; testes de ViewModel cobrem as três saídas por
  tela.
- **`transactions` deixa de ser campo de topo das UiStates** → quebra local, contida nas telas
  e nos testes dos módulos `impl`; nenhuma api expõe UiState.
- **Migrar `TransactionsEmptyState` para o componente compartilhado toca uma feature que este
  change não motiva** → a alternativa é deixar a cópia idêntica ao lado do original; a migração
  é mecânica, sem mudança visual, e os testes de `transactions/impl` seguem verdes.
- **A tela de contas tem um `IndexOutOfBounds` latente** (`domainAccounts[selectedAccountIndex]`
  com lista vazia) que este change não corrige, por ser outro assunto e hoje inalcançável (ver
  Non-Goals) → fica registrado aqui; se algum dia a conta padrão puder ser retirada, é essa
  linha que quebra primeiro, não a mensagem de vazio.
