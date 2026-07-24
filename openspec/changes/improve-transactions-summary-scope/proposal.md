## Why

A tela de transações **lista dois livros e resume um só**.

A lista vem de `observeAllTransactions()` (`TransactionsViewModel.kt:52`) e mostra tudo — compras em conta e compras no cartão. O `SummaryCard` acima dela vem de `assetMonthFlows` e `calculateBalanceUseCase` (`TransactionsViewModel.kt:65,80-81`), que só olham contas `ASSET`. Uma compra de R$ 300 no cartão aparece na lista e não entra em nenhuma linha do resumo — nem em "Saídas", nem no "Saldo Final" —, e a tela não tem onde dizer por quê.

O sintoma que o usuário relata é **"a tela é uma visão de conta corrente"**, e a dificuldade que ele descreve é **conciliar fatura e saldo**: os dois números existem, moram em telas diferentes, e não há em lugar nenhum a linha que os une.

O agravante é que o eixo que resolveria isso **já existe e está rebaixado**. `TransactionTarget` (`ACCOUNT`/`CREDIT_CARD`) é hoje um chip que filtra a lista (`TransactionsViewModel.kt:170-173`) e não toca no resumo — então filtrar por "Cartão" produz uma lista de cartão sob um resumo de conta, que é a mesma incoerência com um clique a mais. `TransactionsUiState.CreditCardOverview` (`TransactionsUiState.kt:49`) é código morto sem nenhum leitor: o esqueleto de uma tentativa anterior de resolver exatamente isto.

E o viés não nasce na tela. Ele está na assinatura do razão:

```kotlin
/** Natural balance of [accountId] (or of all ASSET accounts when null) ... */
suspend fun balanceUpTo(target: YearMonth, accountId: Long? = null): Double
```

"Nenhuma conta especificada" significando "todas as contas `ASSET`" é a premissa *saldo = conta corrente* uma camada abaixo da UI — em um razão cujo plano de contas é deliberadamente simétrico.

## What Changes

- **O resumo ganha um escopo, e o escopo governa resumo e lista.** Três valores — **Tudo**, **Contas**, **Cartões** —, cada um com a mesma gramática: **abertura → fluxos → fechamento**, mudando apenas o perímetro.

  | escopo | abertura | fluxos | fechamento |
  |---|---|---|---|
  | Contas | Saldo inicial | Entradas · Saídas · Faturas · Ajustes | Saldo atual/final |
  | Cartões | Dívida inicial | Gastos · Pagamentos · Ajustes | Dívida final |
  | Tudo | Líquido inicial | Entradas · Saídas · **Pagamentos (neutro)** · Ajustes | Líquido |

  O escopo **Contas** é exatamente o card de hoje, sem alteração de valor.

- **A lista passa a acompanhar o escopo**: Contas lista as transações com perna `ASSET`, Cartões as com perna `LIABILITY`, Tudo lista tudo. O que hoje só o chip fazia — mal, porque sem o resumo junto.

- **No escopo Tudo, "Saídas" agrega conta e cartão numa linha só.** Distinguir a origem da saída é responsabilidade do escopo, não do resumo: cada modo responde uma pergunta, não todas.

- **"Pagamentos" no escopo Tudo é informativo, sem sinal.** Um pagamento de fatura sai do caixa e abate a dívida na mesma medida — não move o líquido. Isso não é uma exceção nova: é a regra que o razão **já aplica** ao excluir transferência entre contas de `assetMonthFlows` (`EntryDao.kt:300-330`), generalizada. Muda o perímetro, muda o que é interno a ele:

  | escopo | movimento interno (não é fluxo) |
  |---|---|
  | Contas | transferência entre contas |
  | Cartões | — (o pagamento vem de fora e por isso abate) |
  | Tudo | transferência **e** pagamento de fatura |

  Com isso a coluna fecha nos três escopos, e a identidade `fechamento = abertura + entradas − saídas + ajustes` vale por construção — o pagamento cancela entre as duas pernas.

- **A linha de cada transação passa a ser lida pela perna do escopo.** Um pagamento de fatura aparece nos escopos Contas e Cartões com sinais opostos (`−500` saiu do caixa / `+500` a dívida caiu), porque é o mesmo evento visto de dois livros. Hoje `toTransactionUi` sem perspectiva usa sempre a perna que sai (`TransactionUiMapper.kt:33`), então no escopo Cartões o pagamento apareceria como saída sob um total que diz que a dívida diminuiu.

- **Escopo e período mudam para dentro do card de resumo**, no topo, como dois chips lado a lado. O card deixa de ser um painel passivo e passa a declarar o que está somando logo acima do número. O `MonthSelector` sai do `topBar` (`TransactionsScreen.kt:70-84`) e vira chip **sem as setas** `‹ ›` — só o picker.

- **O chip de alvo sobrevive apenas no escopo Tudo.** Nos outros dois ele seria a mesma decisão em dois controles, com estados contraditórios possíveis (escopo=Contas + chip=Cartão → lista vazia).

- **`TransactionsRoute.filterTarget: TransactionTarget?` → `scope: TransactionScope?`.** Nenhum call site passa `filterTarget` hoje (os quatro do dashboard passam `null` — `DashboardComponentContent.kt:260,296,369,380`), então o parâmetro é substituído sem migração. Os dois cards de entradas/saídas do dashboard, que são de **saldo concreto**, passam a navegar com `ACCOUNTS`, e o resumo de destino passa a bater com o card de origem.

- **Duas adições ao razão**, ambas de simetria:
  - `LiabilityMonthFlows` ganha `adjustment`. A query atual classifica só `eq = 0` (`EntryDao.kt:275-289`), então o ajuste de fatura não cai em `expense` nem em `payment` — hoje isso é invisível porque ninguém tenta fechar a conta do cartão; no escopo Cartões apareceria como `dívida inicial + gastos − pagamentos ≠ dívida final`.
  - Saldo até o mês passa a ser expresso **por natureza de conta**, não só para `ASSET` — o mesmo agregado de `assetsBalanceUpToMonth` (`EntryDao.kt:169-176`) parametrizado pelo tipo. É o que dá a dívida de abertura/fechamento e, somado ao de `ASSET` (passivos são armazenados negativos), o líquido — sem inventar sinal e sem query nova para o terceiro escopo.

- **Default = Tudo.** Hoje a lista já mostra tudo; nascer em Contas esconderia as compras de cartão por padrão, o que é regressão percebida. Nascendo em Tudo, a lista permanece idêntica e o resumo passa a explicá-la — o defeito é corrigido no lado que estava errado.

- **`TransactionsUiState.CreditCardOverview` é removido** (código morto, sem leitor).

### Fora de escopo (confirmado com o usuário)

- **O escopo não sobe para `core`.** Ele permanece um enum de três valores na feature `transactions`, apesar de `ReportPerspective` (`core/model`) e `scopeStats` (`IEntryRepository`) já modelarem a mesma ideia de forma mais geral. Unificar as duas ontologias é trabalho próprio, depois.
- **Os chips de filtro continuam afetando só a lista.** Categoria, natureza, recorrente e parcelado não mudam o resumo. A regra da tela é posicional: o que está no card governa card e lista; o que está abaixo dele governa só a lista.
- **O default enviesado de `balanceUpTo(target, accountId = null)` não é removido.** A change adiciona a leitura simétrica ao lado dele; aposentar o default tem outros consumidores (`CalculateBalanceUseCase`, dashboard) e é change própria.
- **"Período" é mês.** O chip mostra `julho 2026`, não abre intervalo livre — o eixo mês é premissa de praticamente todo agregado do razão (`substr(date,1,7)`).
- **Escopo Cartões usa competência de lançamento**, não ciclo de fatura: o mês do chip é o mês da transação, coerente com a lista logo abaixo. Fatura, que tem ciclo próprio, continua sendo assunto da tela de faturas.
- **Sem as setas `‹ ›` no chip de período.** Decisão explícita do usuário; reavaliar se o uso incomodar.

### Débito de produto registrado

Com os controles dentro do card, que é `item` da `LazyColumn`, escopo e mês **saem de vista ao rolar** — hoje o mês vive no `topBar` e está sempre visível. A decisão é tentar dentro primeiro. Se incomodar: card fixo acima da lista, ou `topBar` colapsante com mês + escopo.

## Capabilities

### New Capabilities
- `transaction-scope`: o escopo de leitura da tela de transações — os três perímetros, a gramática única (abertura → fluxos → fechamento), a identidade aritmética que cada um deve satisfazer, a regra de que movimento interno ao perímetro não é fluxo, e o alcance posicional dos controles (escopo e período governam resumo e lista; filtros governam só a lista).

### Modified Capabilities
- `ledger-reporting`: o saldo até um mês passa a ser derivável **por natureza de conta**, e não apenas para `ASSET`; os fluxos do mês de `LIABILITY` passam a reportar ajuste, em simetria com os de `ASSET`, de modo que abertura + fluxos = fechamento valha para qualquer natureza.
- `presentation-mapping`: a perspectiva de mapeamento de um item de lista passa a poder ser determinada pelo **escopo da tela** — a natureza da conta cuja perna deve ser exibida —, e não apenas por uma conta identificada.

## Impact

- **`core/ledger`** — `LiabilityMonthFlows` ganha `adjustment` e a query de `liabilityMonthTotals` ganha o ramo `eq = 1` (`EntryDao.kt:275-289`); `assetsBalanceUpToMonth` é generalizada para receber o tipo de conta e `IEntryRepository` expõe a leitura por natureza. Nenhuma escrita muda, nenhum valor existente muda.
- **`core/ui`** — `toTransactionUi` passa a aceitar a perna por natureza de conta, além de por `accountId` (`TransactionUiMapper.kt:25-33`). `TransactionPerspective` não muda.
- **`core/designsystem`** — nenhuma cor nova; as cinco já existem.
- **`core/resources`** — strings novas (× idiomas): rótulos dos três escopos, `Dívida inicial`, `Dívida final`, `Gastos`, `Pagamentos`, `Líquido inicial`, `Líquido`.
- **`feature/transactions/api`** — novo `TransactionScope` (`@Serializable`) e seu `NavType`; `TransactionsRoute.filterTarget` → `scope`; `TransactionTargetNavType` sai se não restar consumidor.
- **`feature/transactions/impl`** — `TransactionsFilters` ganha o escopo ou o separa dos filtros (design); `TransactionsAction.SelectScope`; `TransactionsUiState.BalanceOverview` deixa de ser um shape único e passa a variar por escopo; `SummaryCard` recebe os dois chips no topo e o corpo por escopo; `TransactionsScreen` perde o `topBar`; `TargetFilterChip` passa a ser condicional; `CreditCardOverview` removido.
- **`feature/dashboard/impl`** — `openTransactions` troca `TransactionTarget?` por `TransactionScope?`; os dois cards de saldo concreto passam `ACCOUNTS`, os outros dois seguem `null`.
- **Testes** — a identidade `fechamento = abertura + entradas − saídas + ajustes` por escopo (incluindo o caso com ajuste de fatura, que hoje falharia); a neutralidade do pagamento no escopo Tudo; o sinal do pagamento em Contas vs. Cartões; a paridade entre o total do escopo e a lista que ele governa. `TransactionsViewModelCharacterizationTest` caracteriza o resumo atual — ele passa a ser o caso do escopo Contas.
- Sem migração de banco, sem mudança de escrita, sem mudança em nenhuma figura já exibida.
