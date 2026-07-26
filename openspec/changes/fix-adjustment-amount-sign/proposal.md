## Why

Um ajuste de saldo é a única transação bidirecional do app — ele diz *para que lado* o saldo foi corrigido — e é a única cujo valor é exibido **sempre com `+`**, em qualquer direção. `TransactionUiMapper` descarta o sinal da perna com `abs()`, e o `TransactionCard` chama `formatWithSign` sobre um valor já em módulo: o ramo negativo é inalcançável. Aumentar a dívida de um cartão de R$ 0,00 para R$ 100,00 lê `+R$ 100,00`; reduzir o saldo de uma conta também.

A causa é que o app **descobriu a regra de sinal mas nunca a escreveu**. Os resumos a aplicam corretamente — o `SummaryCard` resolve linha a linha por perspectiva e explica o porquê em KDoc, e o resumo da fatura, o `AccountCard` e o relatório fazem o mesmo, cada um com seu mecanismo. A superfície de **item** — o card de transação e o relatório exportado — nunca a recebeu. Sete sítios implementam a mesma decisão de forma independente; dois deles carregam o defeito.

## What Changes

- Escrever a regra como regra, com um princípio único: **o sinal expressa o efeito do valor sobre o patrimônio da perspectiva em que é lido** — omitido onde o rótulo já entrega a direção, omitido onde não há perspectiva sobre a qual haja efeito, e exibido onde há aritmética a explicar.
- Novo tipo `DisplayAmount` em `:core:common`, ao lado de `CurrencyFormatter`: dono único do valor de exibição e da política de sinal (`MAGNITUDE`, `NATURAL`, `NEUTRAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, `OWED`). Sem operação entre dois valores e sem moeda — ele diz *como se lê*, nunca *quanto vale*, que é do razão.
- `TransactionUiMapper` deixa de aplicar `abs()` e resolve a política de cada item pela regra.
- **BREAKING (interno)**: `TransactionUi.amount` deixa de ser `Double` sempre positivo e passa a ser `DisplayAmount`. Consumidores fora de `:core:ui` são dois, ambos em `feature/report/impl`.
- **Mudança visual 1** — o **ajuste** passa a exibir o sinal correto em toda superfície de item: negativo quando reduz o patrimônio (dívida que sobe, saldo que desce), positivo quando o aumenta. É o defeito.
- **Mudança visual 2** — a **transferência sob a perspectiva de uma conta** passa a exibir sinal explícito nas duas pontas: `−` na origem, como hoje, e `+` no destino, que hoje aparece sem sinal. As duas pontas compartilham rótulo, ícone e cor; sinalizar só uma obrigaria a inferir a outra por ausência.
- **Mudança visual 3** — a **transferência sem perspectiva** deixa de exibir o `−` que hoje é composto à mão. Vale para todas as listas que não declaram perspectiva — transações, dashboard, cartões, parcelamentos e relatório; só a tela de contas passa `accountId`.
- **Mudança visual 4** — no ramo de **fatura** do relatório, o gasto passa a exibir `−` e o pagamento antecipado `+`, alinhando-o ao ramo de conta do mesmo relatório, que já exibe. É a única parte dos resumos que não obedecia à regra.
- A escolha da política sai dos `@Composable` e passa para quem produz a figura — `BalanceOverviewFactory`, `AccountsViewModel`, `InvoiceTransactionsViewModel` e `ReportViewerUiState.Stats` —, nas duas superfícies. Sem isso a change escreveria um requisito que ela mesma violaria em cinco dos sete sítios.
- `TransactionCard` e `ReportExportLayout.exportAmount` perdem seus `when` duplicados e a concatenação manual de `"-"`.
- `ReportExportLayout.exportTone` passa a acertar o tom do ajuste — hoje todo ajuste cai em `ReportTone.POSITIVE`, porque o ramo que decide é `amount >= 0` e `amount` é sempre positivo (o ramo `NEGATIVE` é inalcançável).
- Absorção dos demais sítios da mesma política, **sem mudança de comportamento**, porque já obedecem à regra: o `enum SignDisplay` de `SummaryCard` (18 usos — a implementação de referência, de onde o tipo novo nasce), o `private enum AccountSignDisplay` de `AccountCard`, o trio de `Boolean` do `SummaryRow` da fatura, e quatro literais `"+${...}"`/`"-${...}"` em `ReportContextCard` e `ReportExportLayout`.
- `TransactionUiMapper` passa a usar `Transaction.primaryEntry` em vez de reimplementar a escolha da perna neutra.
- As três telas que **têm** uma perspectiva de cartão e não a declaravam passam a declará-la — extrato da fatura, tela de cartões e o ramo de cartão do relatório —, e o filtro por tipo da fatura, que reimplementava a escolha da perna à mão, passa a consumi-la. Sem isso a change fecharia deixando de pé a mesma classe de defeito que ela nomeia: uma escolha de perna com dois donos, na mesma tela.

## Capabilities

### New Capabilities
- `money-display`: o princípio do sinal exibido, a política de sinal como tipo de dono único, e a sua aplicação nas duas superfícies — item e resumo.

### Modified Capabilities
- `presentation-mapping`: *se* uma perna de transação exibe sinal é decisão da política de exibição; *qual* sinal, quando exibe, é o natural do razão — a inversão por `AccountType` é regra de **saldo**. Hoje a spec só exemplifica o caso da inversão, o que induz à leitura oposta. Adiciona também o dono único da escolha da perna neutra e a obrigação de uma tela declarar a perspectiva que ela tem.

## Impact

**Código**
- `core/common`: novo `DisplayAmount` + extensão de formatação sobre `CurrencyFormatter`.
- `core/ui`: `TransactionUiMapper`, `TransactionUi.amount`, `TransactionCard`, `AccountCard`, `AccountUi`, e `implementation(core.common)` → `api(...)`.
- `feature/report/impl`: `ReportExportLayout` (`exportAmount`, `exportTone`, linhas de resumo), `ReportContextCard` e `ReportViewerUiState.Stats`.
- `feature/transactions/impl`: `SummaryCard` (`SignDisplay` e seus 18 usos), `BalanceOverviewFactory`, `ViewTransactionUiState` e `ViewTransactionModal`.
- `feature/creditcards/impl`: `InvoiceTransactionsScreen.SummaryRow` e `InvoiceTransactionsViewModel` (resumo, perspectiva e filtro por tipo), `CreditCardsScreen`.
- `feature/accounts/impl`: `AccountsViewModel`, onde `AccountUi` é montado.

**Telas a verificar**: transações (lista + os três corpos do `SummaryCard`), contas, cartões/extrato da fatura, parcelamentos, dashboard, relatório exportado (tela e HTML/PDF).

**Testes**: `core/ui/.../TransactionPerspectiveTest` codifica a semântica de módulo e será atualizado.

**Fora de escopo, registrado**: nenhum. A ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` estava registrada aqui como defeito separado, sob a justificativa de que passaria a afetar a **transferência**. A justificativa era falsa: uma transferência é `ASSET → ASSET` e não aparece em nenhuma das três — as duas primeiras têm perspectiva de *cartão*, e o relatório, quando é de contas, é de várias. O que de fato sobrava ali era a **escolha da perna com dois donos** dentro da mesma tela, e por ser exatamente a classe de defeito que esta change nomeia, entrou no escopo (grupo 12) em vez de virar débito.
