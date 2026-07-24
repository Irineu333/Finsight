## Why

A lista de transações **filtra por um vocabulário e exibe por outro**, e os dois não coincidem.

O razão tem dois conceitos distintos e ambos legítimos: `TransactionLabel` — a **natureza** da transação, derivada dos tipos de conta das entries (`EXPENSE`/`INCOME`/`TRANSFER`/`PAYMENT`/`ADJUSTMENT`, `Ledger.kt:56`) — e `TransactionType` — a **direção de uma perna** vista de uma perspectiva (`EXPENSE`/`INCOME`/`ADJUSTMENT`, `Ledger.kt:96`), que a doc do próprio enum descreve como vocabulário de **entrada** (design D4).

O card da lista renderiza `label` (`TransactionUiMapper.kt:39`, `TransactionCard.kt:145,168`). O filtro, porém, usa `TransactionType` sobre `primaryEntry` (`TransactionsViewModel.kt:159-164`) — e `primaryEntry` é sempre a perna que **sai** (`Transaction.kt:50`, `minByOrNull { it.amount }`). Logo:

| transação | entries | `primaryEntry` | filtro vê | card exibe |
|---|---|---|---|---|
| compra | `ASSET −100` / `EXPENSE +100` | `ASSET −100` | EXPENSE | EXPENSE |
| transferência | `ASSET −100` / `ASSET +100` | `ASSET −100` | **EXPENSE** | TRANSFER |
| pagto. de fatura | `ASSET −100` / `LIABILITY +100` | `ASSET −100` | **EXPENSE** | PAYMENT |
| salário | `ASSET +100` / `INCOME −100` | `ASSET +100` | INCOME | INCOME |

Daí os dois sintomas, que são o mesmo defeito: **"Despesa" lista transferências e pagamentos**, e **transferência e pagamento não têm como ser filtrados** — `TransactionType.entries` (`TransactionsScreen.kt:343`) só tem três valores, o dropdown é estruturalmente incapaz de oferecê-los.

O agravante é a discordância **dentro da mesma tela**: o `SummaryCard` acima da lista vem do razão (`assetMonthFlows` e `liabilityMonthFlows().payment`, `TransactionsViewModel.kt:65,75`), que exclui transferência e pagamento da despesa e reporta pagamento como linha própria. O cabeçalho diz "Despesa R$ 1.200" e a lista filtrada por "Despesa" logo abaixo soma R$ 4.000. Uma feature reimplementou uma classificação que o razão já deriva — exatamente o que a Derivation Rule proíbe e o que `balanced-ledger` declara ("nenhuma feature reimplementa regra derivável daqui").

## What Changes

- **O eixo "Tipo" da lista de transações passa a filtrar por `TransactionLabel`.** O filtro deixa de re-derivar classificação e passa a ler `transaction.label` — a mesma propriedade que o card já usa para desenhar. Filtro e card deixam de ter como discordar, porque leem o mesmo dono.
- **O dropdown ganha as duas opções que faltavam** — Transferência e Pagamento — passando de 3 para 5. Como `deriveTransactionLabel` é total e mutuamente exclusivo sobre as sete formas do razão, as cinco opções **particionam** a lista: a união dos filtros é exatamente a lista sem filtro. Hoje isso não vale.
- **Sem filtro continua listando tudo**, inclusive transferência, pagamento e ajuste. "Despesa" vira subconjunto próprio, não o default por vazamento.
- **Cor por natureza, cinco cores.** Quatro já existem no tema com exatamente os valores desejados (`Income` verde, `Expense` vermelho, `Adjustment` amarelo, `InvoicePayment` roxo); falta o azul de transferência — constante nova `Transfer` (blue-500), completando a família Tailwind-500 do arquivo. O roxo é o mesmo que o `SummaryCard` já usa na linha "Pagamento": chip do filtro e linha do resumo passam a ter a mesma cor para a mesma coisa.
- **`TransactionsRoute` passa a carregar `filterLabel: TransactionLabel?`** no lugar de `filterType`, com `TransactionLabelNavType` espelhando o `TransactionTypeNavType` (que sai, sem outro consumidor). Os dois únicos call sites que passam tipo são do dashboard (`DashboardComponentContent.kt:369,380`) e mapeiam 1:1.
- **`TransactionType` sai do caminho de leitura desta tela** e volta a ser só o que a sua doc diz: vocabulário de entrada (`RecurringForm`, analytics) e direção **sob perspectiva**.

### Fora de escopo (confirmado com o usuário)

- **`primaryEntry` não muda.** Transferência continua sendo **uma** linha, olhada pela perna de saída — não duas, e não se confunde com entrada/saída.
- **`hasLiabilityLeg` não muda.** O filtro de alvo "Cartão de crédito" continua significando **qualquer movimentação no cartão**, incluindo o pagamento da fatura.
- **A tela de faturas não é tocada.** `InvoiceTransactionsViewModel.kt:254-263` usa `TransactionType` **corretamente**: lê a perna `LIABILITY` do próprio cartão, uma perspectiva explícita, onde "entrada" legitimamente se chama "Pagamento" (`invoice_transactions_filter_type_payment`). Direção sob perspectiva é o uso certo de `TransactionType`; o defeito é usá-lo como natureza numa lista **sem** perspectiva.
- **`ADJUSTMENT` continua um balde de dois** (ajuste de saldo e de fatura), como já é hoje. O card os distingue no título; o filtro não. Não é regressão — `deriveTransactionType` funde os dois do mesmo jeito.

## Capabilities

### New Capabilities
<!-- Nenhuma: o mapeamento domínio→apresentação já mora em presentation-mapping. -->

### Modified Capabilities
- `presentation-mapping`: acrescenta o requisito de que **filtrar por natureza usa a derivação do domínio**, e fixa a distinção entre os dois vocabulários — natureza (`TransactionLabel`, sem perspectiva) e direção (`TransactionType`, sob perspectiva) —, incluindo a totalidade/exclusividade que faz o eixo de filtro particionar a lista e a paridade com os agregados que o razão já reporta.

## Impact

- **`core/ledger`** — nada. `TransactionLabel` e `Transaction.label` já existem e já são o dono da natureza.
- **`core/designsystem`** — nova constante `Transfer = Color(0xFF3B82F6)` em `Color.kt`. Constante própria, **não** reuso de `Info` (`:39`) ou `CategoryColor` (`:49`), que hoje têm o mesmo valor com outra semântica.
- **`core/resources`** — 2 strings novas × 2 idiomas: `transactions_filter_type_transfer`, `transactions_filter_type_payment`.
- **`feature/transactions/api`** — `TransactionsRoute.filterType: TransactionType?` → `filterLabel: TransactionLabel?`; novo `TransactionLabelNavType`; `TransactionTypeNavType` removido (único consumidor era o grafo desta feature).
- **`feature/transactions/impl`** — `TransactionsFilters.type` → `label`; `TransactionsAction.SelectType` → `SelectLabel`; `TransactionsUiState.selectedType` → `selectedLabel`; o filtro do ViewModel vira `it.label == label` (some o `deriveTransactionType` do arquivo); `TypeFilterChip` passa a 5 opções e 5 cores; `TransactionsGraph` troca o `typeMap`; `TransactionsModule` acompanha o rename do parâmetro.
- **`feature/dashboard/impl`** — `openTransactions` muda de assinatura e os 2 call sites passam `TransactionLabel.INCOME`/`EXPENSE`.
- **Testes** — o `TransactionsViewModelCharacterizationTest` caracteriza **só o resumo do mês**, não o filtro: ele acompanha o rename do parâmetro e nada mais. Falta cobertura do eixo, e ela entra agora: um teste por natureza, mais o teste de **partição** (união dos cinco = sem filtro) e o de **paridade** com o que o razão reporta.
- Sem migração de banco, sem mudança de escrita, sem mudança em nenhuma figura do razão.
