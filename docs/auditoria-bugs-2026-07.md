# Auditoria de bugs e casos de borda — julho/2026

**Escopo:** todas as features + core, no estado de `main` em `fb7dfe8be`.
**Método:** varredura por área, seguida de uma passagem de verificação adversarial independente
(cada achado foi reavaliado com a instrução de assumi-lo falso até o código provar o contrário).
**Resultado:** 73 achados brutos → 65 verificados → **9 refutados**, 18 rebaixados por premissa
parcialmente falsa, 38 confirmados.

Nenhum arquivo foi modificado. Todas as linhas citadas foram lidas; onde a verificação corrigiu a
auditoria original, a correção está registrada no próprio item.

---

## Sumário por criticidade

| # | Achado | Área | Tipo |
|---|---|---|---|
| **CRÍTICO** |
| [1](#1) | Editar `closingDay`/`dueDay` quebra o invariante `dueDate >= closingDate` | creditcards | dados |
| **ALTO** |
| [2](#2) | Exclusão de fatura futura pode crashar (exceção escapa do `Either`) | creditcards | crash |
| [3](#3) | Exceção de leitura em `stateIn` encerra o processo no Android | transversal | crash |
| [4](#4) | Duplo clique duplica pagamento, transferência, ajuste e confirmação | transversal | concorrência |
| [5](#5) | Parcelamento não fecha com o total declarado (centavos) | creditcards | dados |
| [6](#6) | Excluir recorrente zera o limite de orçamento `PERCENTAGE` | budgets | dados |
| [7](#7) | Ajuste de saldo **inicial** parte da baseline errada | accounts | correção |
| [8](#8) | `Either` da abertura de fatura é descartado → cartão sem fatura `OPEN` | creditcards | dados |
| [9](#9) | Orçamento `PERCENTAGE` fica impossível de editar se o recorrente é pausado | budgets | correção |
| [10](#10) | Auto-scroll do chat pode chamar `animateScrollToItem(-1)` | support | crash |
| [11](#11) | `IconPickerModal` sem scroll — ícones inalcançáveis | designsystem | UX |
| **MÉDIO** |
| [12](#12) | Pagamento de fatura classificado como despesa só no relatório | ledger | correção |
| [13](#13) | Falha de domínio vira "o botão não faz nada" (5 pontos) | transversal | UX |
| [14](#14) | Data de pagamento de fatura em atraso é falseada | creditcards | dados |
| [15](#15) | Recorrente atrasado mais de um mês desaparece | recurring | dados |
| [16](#16) | Valor negativo aceito no formulário de transação | transactions | dados |
| [17](#17) | "Sem categoria" some do detalhamento e distorce os percentuais | report | dados |
| [18](#18) | Percentual de categoria pode passar de 100% ou ficar negativo | categories | correção |
| [19](#19) | Índice posicional de fatura muda sozinho | creditcards | UX |
| [20](#20) | `BudgetRepository.insert/update` não são atômicos | budgets | dados |
| [21](#21) | N+1 de queries no dashboard e no cálculo por categoria | dashboard | performance |
| [22](#22) | Todo o trabalho de agregação roda na main thread | transversal | performance |
| [23](#23) | Leituras de detalhe carregam a tabela `entries` inteira | ledger | performance |
| [24](#24) | Round-trip monetário quebra em locales sem 2 casas decimais | common | dados |
| [25](#25) | Nome de conta: `isEmpty()` em vez de `isBlank()`, trim assimétrico | accounts | correção |
| [26](#26) | Limite disponível ignora faturas `RETROACTIVE` | creditcards | dados |
| [27](#27) | Navegação para issue sem `launchSingleTop` | support | navegação |
| [28](#28) | Mês destacado em qualquer ano no picker | designsystem | UX |
| [29](#29) | 8 strings sem tradução em `values-en` | resources | i18n |
| [30](#30) | Filtro do Support produz lista vazia sem estado vazio | support | UX |
| **BAIXO** |
| [31](#31) | Fatura com saldo negativo não fecha e não explica | creditcards | UX |
| [32](#32) | Faturas `FUTURE` órfãs quando um parcelamento falha | creditcards | dados |
| [33](#33) | Janela de recorrentes próximos não atravessa a virada do mês | dashboard | UX |
| [34](#34) | `contentDescription = null` em 9 botões de navegação | ui | acessibilidade |
| [35](#35) | Resposta do Support: texto limpo antes de confirmar o envio | support | UX |
| [36](#36) | `startKoin` dentro de `MainViewController()` no iOS | app | correção |
| [37](#37) | Rótulo de perspectiva do relatório engana quando a conta some | report | UX |
| [38](#38) | Achados menores agrupados | vários | — |

---

## Críticos

### <a name="1"></a>1. Editar `closingDay`/`dueDay` quebra o invariante `dueDate >= closingDate`

`UpdateCreditCardUseCase.kt` · `CreditCardFormViewModel.kt:172-180` · `Invoice.kt:27-29`

`UpdateCreditCardUseCase` grava qualquer alteração sem validar a relação entre os dois dias —
nem `CreditCardForm.build()` nem `CreditCard.init` checam (só a faixa `1..31`). Mas as datas da
fatura são **derivadas em tempo de leitura** do cartão:

```kotlin
val closingDate get() = closingMonth.safeOnDay(creditCard.closingDay)
val dueDate     get() = dueMonth.safeOnDay(creditCard.dueDay)
```

enquanto `dueMonth` foi **congelado** na criação conforme a regra `dueDay < closingDay`
(`OpenInvoiceUseCase.kt:50-54`). Editar os dias reescreve retroativamente as datas de todo o
histórico e pode inverter a ordem entre fechamento e vencimento.

**Repro:** cartão `closingDay=5`, `dueDay=10` (→ `dueMonth == closingMonth`). Fatura fecha 05/08,
vence 10/08. Editar para `dueDay=1` → `dueDate = 01/08 < closingDate = 05/08`.
Consequências encadeadas:
- a fatura fica **impagável** — `paidAt >= closingDate` e `paidAt <= dueDate` são contraditórios;
- abrir `PayInvoiceModal` lança `IllegalArgumentException` no `coerceIn` (`PayInvoiceModal.kt:70`)
  **durante a composição**;
- `GetOrCreateInvoiceForMonthUseCaseImpl.kt:28` busca por `dueMonth` e passa a criar faturas
  duplicadas onde já existia uma.

Este é o achado de maior alcance: é ele que torna [14](#14) e o ramo de falha de
`CloseInvoiceUseCase` atingíveis.

**Correção sugerida:** congelar `closingDay`/`dueDay` na `InvoiceEntity` na criação (as datas
passam a ser propriedade da fatura), ou recusar a edição desses campos quando existir fatura
não-`FUTURE`. A segunda é mais barata e preserva o histórico.

---

## Altos

### <a name="2"></a>2. Exclusão de fatura futura pode crashar

`DeleteFutureInvoiceUseCase.kt:28-34` · `DeleteFutureInvoiceViewModel.kt:21`

A assinatura promete `Either<InvoiceException, Unit>`, mas `deleteTransactionById` **lança**
(`TransactionRepository.kt:312-313` → `ClosedAccountException`), e `either {}` do Arrow não
intercepta exceção lançada — só `Raise`. O `PayInvoicePaymentUseCase.kt:32-37` documenta
exatamente esse problema e o resolve com `catch{}.bind()`; este caminho não.

**Repro confirmado:** fatura `RETROACTIVE` (também deletável, `Invoice.kt:85-87`) com pagamento
antecipado feito da conta A → zerar e arquivar a conta A → excluir a fatura →
`ClosedAccountException` escapa do `Either` → crash no `viewModelScope.launch`.

Secundário: as N remoções ocorrem em N transações separadas; falha no meio deixa a fatura
parcialmente esvaziada. `deleteTransactionsByIds(...)` é atômico e já existe.

### <a name="3"></a>3. Exceção de leitura em `stateIn` encerra o processo no Android

`BudgetsViewModel.kt:49` · `CategoriesViewModel.kt:43` · `ViewCategoryViewModel.kt:72` ·
`DashboardViewModel.kt:139` · `ReportViewerViewModel.kt:231` · `ReportConfigViewModel.kt:58`

Nenhum tem `.catch` antes do `stateIn` (`grep` confirma **zero** `Flow.catch` no projeto fora de
`feature/support`). Semântica verificada passo a passo:

1. o bloco do `combine` roda dentro do flow upstream — a exceção propaga pela coleta;
2. `SharingStarted` **não captura nada**: não há `try/catch` em `launchSharing`;
3. `viewModelScope` usa `SupervisorJob`, então o escopo **não é cancelado** e os irmãos sobrevivem
   *(correção à auditoria original, que alegava cancelamento)*;
4. justamente porque o pai recusa a falha, `JobSupport` chama `handleCoroutineException`;
5. sem `CoroutineExceptionHandler` no contexto, cai no `uncaughtExceptionHandler` da thread —
   no Android, o `KillApplicationHandler` do `RuntimeInit` **mata o processo**.

Ressalva de portabilidade: em Desktop/JVM o mesmo caminho apenas imprime o stack trace e o
`StateFlow` congela no `initialValue`. O sintoma é plataforma-dependente.

Os candidatos concretos a lançar são as chamadas suspensas de Room dentro dos blocos
(`entryRepository.*`, `calculateReportStatsUseCase`, `invoices.minOf`). Nenhum `UiState` das
quatro features tem um `Error` alcançável por falha de leitura.

### <a name="4"></a>4. Duplo clique duplica a operação

Nenhum ViewModel de escrita tem estado de submissão em voo, e nenhum botão é desabilitado por
outro meio:

| ViewModel | Botão |
|---|---|
| `PayInvoiceViewModel.kt:72-104` | `PayInvoiceModal.kt:173` — só data + conta |
| `AdvancePaymentViewModel.kt:72-89` | `AdvancePaymentModal.kt:173` — idem |
| `CloseInvoiceViewModel.kt:21` | `CloseInvoiceModal.kt:72` — **sem `enabled`** |
| `ConfirmRecurringViewModel.kt:137-161` | `ConfirmRecurringModal.kt:248` — só valor |
| `AddTransactionViewModel.kt:115-144` | `AddTransactionModal.kt:284` — só validade do form |
| `EditTransactionViewModel.kt:137-157` | — |
| `TransferBetweenAccountsViewModel.kt:80-99` | `TransferBetweenAccountsModal.kt:166` |
| `EditAccountBalanceViewModel.kt:104-141` | `EditAccountBalanceModal.kt:183` |

O `dismiss()` só ocorre em `onRight`, depois da escrita. O `InvoiceWriteGuard` **não** protege:
ele veta escrita em fatura `PAID`, e o status só muda depois da gravação.

**Repro (pior caso, antecipação):** fatura com R$ 100 devidos, dois toques rápidos em "Confirmar"
→ ambos passam `ensure(amount <= currentBillAmount)` (`AdvanceInvoicePaymentUseCase.kt:63`) antes
de qualquer escrita → duas transações de R$ 100 → fatura com saldo −100.

**Repro (recorrente):** `require(existingOccurrence?.status != CONFIRMED)`
(`ConfirmRecurringUseCase.kt:57-60`) também lê antes de gravar; o upsert por
`(recurringId, yearMonth)` deixa **uma** ocorrência apontando para a segunda transação e **duas**
transações no ledger. O índice único não impede a duplicação.

**Repro (ajuste de saldo):** duas corrotinas leem `existingTransaction == null`
(`AdjustBalanceUseCase.kt:43-53`) e ambas gravam — a idempotência prometida não se aplica porque
leitura e escrita não estão na mesma transação de banco.

### <a name="5"></a>5. Parcelamento não fecha com o total declarado

`AddInstallmentUseCaseImpl.kt:136-140` · `LedgerEntryWriter.kt:181`

A cota é `Double` (`baseLeg.amount / invoices.size`) e cada perna é arredondada
**independentemente** com `(amount * 100).roundToLong()`. Nenhum ponto intermediário redistribui o
resto (verificados `getInvoices`, `registerTransactions` e `createTransactions`).

| n | cota | centavos/parcela | Σ |
|---|---|---|---|
| 3 | 33.333333333333336 | 3333 | **99,99** |
| 6 | 16.666666666666668 | 1667 | **100,02** |
| 7 | 14.285714285714286 | 1429 | **100,03** |
| 12 | 8.333333333333334 | 833 | **99,96** |

Agrava: `createInstallment(totalAmount = base.legs.first().amount)` grava **100,00** — o
parcelamento afirma um total que o ledger não soma. `Σ = 0` continua válido *por transação*; o erro
está no total. O KDoc de `InstallmentRemovalReconciler` reconhece a divergência *para menos*; a
divergência *para mais* (n=6, 7) não está prevista em lugar nenhum.

**Correção:** `val cents = (total*100).roundToLong(); val base = cents / n; val rest = cents % n`,
somando `rest` à primeira parcela.

### <a name="6"></a>6. Excluir recorrente zera o limite de orçamento `PERCENTAGE`

`CalculateBudgetProgressUseCase.kt:46-57,65` · `BudgetEntity.kt:25` · `RecurringRepository.kt:94-101`

Quatro elos, todos confirmados:
- `budgets.recurringId` é uma **coluna nua**, sem FK;
- `DeleteRecurringUseCase` não consulta orçamentos (ver [38](#38): não existe
  `hasBudgetForRecurring`, embora `hasBudgetForCategory` exista e seja aplicado);
- o limite é **recalculado na leitura**, descartando o `budget.amount` persistido:
  `budget.copy(amount = limit)` com `limit` vindo de `recurringList.find { … } ?: 0.0`;
- `coerceIn` **não normaliza `NaN`** (ambas as comparações são `false`), então `0.0/0.0` sobrevive
  até a UI.

**Correção de severidade em relação à auditoria original:** o dano real **não** é o `NaN`, e
**não há crash** — desempacotando `ui-graphics`, `lerp(Color, …)` e o construtor `Color(...)` usam
`fastCoerceIn`, não `require`, então o resultado é cor indefinida e barra sem largura. O defeito
grave é o limite virar silenciosamente **R$ 0,00 em qualquer mês**: num mês com gasto,
`spent/0.0 = +Infinity` → `coerceIn` devolve `1.0`, `isExceeded == true` e o card exibe
"excedido em \<todo o gasto\>". Dado errado, sem nenhum sinal de erro.

### <a name="7"></a>7. Ajuste de saldo **inicial** parte da baseline errada

`EditAccountBalanceViewModel.kt:51-58` · `AdjustOpeningBalanceUseCase.kt:31` ·
`EditAccountBalanceModal.kt:88,183`

O ViewModel usa `calculateBalanceUseCase(target = targetMonth)` para os três tipos. Confrontando
com a baseline real de cada use case: `CURRENT` e `FINAL` são **coerentes**; só `INITIAL` diverge,
porque `AdjustOpeningBalanceUseCase` opera sobre `targetMonth.minusMonth().lastDay`.

**Repro:** conta com abertura R$ 100 e saldo final R$ 300 no mês. Abrir "saldo inicial" → o campo
mostra **R$ 300**. Digitar o valor correto (R$ 100) → botão habilita → o use case compara 100
contra 100 → `AccountNotAdjustedException` → a modal fecha **em silêncio, sem gravar nada**. E
manter R$ 300 — que seria uma mudança real de abertura — deixa o botão **desabilitado**.

### <a name="8"></a>8. `Either` da abertura de fatura é descartado

`CloseInvoiceUseCase.kt:81-86` · `AddCreditCardUseCase.kt:45-58`

Nos dois pontos o resultado de `openInvoiceUseCase(...)` é ignorado — sem `.bind()`, sem `onLeft`.
No `AddCreditCardUseCase` a chamada está dentro de `onRight`, **fora** do `either {}`, então nem
haveria como propagar. Falhas engolidas: `CreditCardNotFound` e `OverlappingInvoice`
(`OpenInvoiceUseCase.kt:36-38,75-77`).

Resultado: cartão/ciclo sem nenhuma fatura `OPEN`. A partir daí toda compra falha em
`GetOrCreateInvoiceForMonthUseCaseImpl.kt:41-42` com `NoOpenInvoice`, e não há tela que ofereça
"abrir fatura" para sair do estado.

### <a name="9"></a>9. Orçamento `PERCENTAGE` impossível de editar se o recorrente é pausado

`BudgetFormViewModel.kt:103-106` · `BudgetFormUiState.kt` · `BudgetFormModal.kt:211`

`StopRecurringUseCase.kt:12` realmente seta `isActive = false`. O ViewModel hidrata o recorrente
**armazenado** a partir de `incomeRecurrings`, que já está filtrada por `isActive` → resolve para
`null` → `canSubmit == false` → botão desabilitado, sem nenhuma mensagem. O usuário não consegue
nem renomear o orçamento.

É o mesmo bug que `BudgetRepository.observeAllBudgets()` já corrigiu para *categorias*
("resolver uma referência armazenada, não oferecer uma escolha"), não aplicado ao recorrente.
O cálculo de progresso não é afetado (`BudgetsViewModel.kt:32` usa `observeAllRecurring()`).

### <a name="10"></a>10. Auto-scroll do chat pode chamar `animateScrollToItem(-1)`

`SupportIssueScreen.kt:147-153`

```kotlin
LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) {
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
}
```

O guard protege contra lista vazia, **não** contra `layoutInfo` ainda não medido. Na versão do
projeto (Compose Multiplatform 1.10.1) a precondição existe:
`LazyLayoutScrollScope.kt:116` → `requirePrecondition(index >= 0)`, que lança
`IllegalArgumentException`.

**Ressalva honesta:** a ordem exata entre o disparo do efeito e a primeira passada de medição não
pôde ser provada por leitura — é o comportamento documentado (`layoutInfo` vazio até a primeira
medição), mas é o único elo não comprovado. Independentemente disso, `totalItemsCount - 1` sem
clamp é inseguro por construção.

**Efeito secundário certo:** ao chegar mensagem nova, o efeito usa a contagem anterior e rola para
a penúltima linha, deixando a mensagem recém-enviada fora da tela.

### <a name="11"></a>11. `IconPickerModal` sem scroll

`IconPickerModal.kt:43-100` · `ModalManager.kt:99-123`

O `Column` do sheet não tem `verticalScroll`, e o único wrapper (`ModalBottomSheet` do Material3)
passa o conteúdo direto — nenhum scroll intermediário. `CategoryFormModal.kt:159-166` passa
`FeatureIconCatalog.withGeneral(categories)` = 44 + 14 ícones, dos quais 6 colidem → **~50**. Com
tiles de 64.dp e ~4 por linha, são ~13 linhas ≈ 900+ dp num sheet com `skipPartiallyExpanded`.

As últimas linhas ficam cortadas e **inalcançáveis**. Mesmo caso em `AccountFormModal.kt:173`,
`BudgetFormModal.kt:194` e `CreditCardFormModal.kt:229`.

---

## Médios

### <a name="12"></a>12. Pagamento de fatura classificado como despesa só no relatório

`EntryDao.kt:390-421` (`scopeStats`) vs `:215-232` (`accountPeriodTotals`) e `:313-333`
(`assetMonthTotals`)

- `accountPeriodTotals` tem as flags `eq` **e** `li`, e classifica `eq=0 AND li=1` como
  **`settlement`**, fora de `income`/`expense`;
- `assetMonthTotals` exige contraparte nominal ou `EQUITY`, excluindo o pagamento;
- `scopeStats` só tem `eq`. Sua única exclusão é a de transferência interna, via
  `COUNT(DISTINCT accountId) >= 2` — e um pagamento de fatura tem **uma** perna `ASSET`, então a
  condição é falsa e a perna entra como `expense`.

**Repro (fixture do `ReportStatsQueryTest`):** contas `1` ASSET e `200` LIABILITY;
`transaction("2026-03-16", 1L posts -3_000, 200L posts 3_000)`. `scopeStats` → `expense = 3_000`;
`accountPeriodTotals` → `expense = 0, settlement = 3_000`; `assetMonthTotals` → `expense = 0`.
Três leituras do mesmo fato, três respostas. Não há teste cobrindo a perspectiva de conta, e o
escopo default do relatório é *todas* as contas ASSET.

É divergência semântica, não corrupção — "saiu dinheiro da conta" é defensável. Mas o domínio já
decidiu o contrário **duas vezes**, o que caracteriza violação da regra de dono único.

### <a name="13"></a>13. Falha de domínio vira "o botão não faz nada"

Padrão `.onLeft { crashlytics.recordException(it) }` sem `modalManager.showError`, em 5 pontos:

| Local | Cenário |
|---|---|
| `AddTransactionViewModel.kt:118-130` (parcelamento) | compra em 6x numa fatura fechada |
| `CloseInvoiceViewModel.kt:25-27` | fatura com saldo negativo ([31](#31)) |
| `AccountFormViewModel.kt:135-136` (edição) | `AccountError.ALREADY_EXIST` |
| `AccountFormViewModel.kt:148-149` (criação) | idem |
| `SupportIssueViewModel.kt:79-81` | ver [35](#35) |

O caminho de transação única (`AddTransactionViewModel.kt:137-139`) faz certo, e o KDoc logo
abaixo (`:146-149`) descreve exatamente este defeito como já corrigido: *"o usuário via um modal
que simplesmente se recusava a fechar"*. A correção não foi propagada.

### <a name="14"></a>14. Data de pagamento de fatura em atraso é falseada

`PayInvoiceUseCase.kt:50-52` · `PayInvoiceModal.kt:66,70`

`ensure(paidAt <= invoice.dueDate)` e `maxDate = dueDate.coerceAtMost(currentDate)`.

**Correção à auditoria original:** o pagamento **não é bloqueado**. O campo é pré-preenchido com
`dueDate` (o `coerceIn` colapsa nele) e o botão fica habilitado. O que se perde é a **data
verdadeira**: pagar dia 15 uma fatura vencida dia 10 grava dia 10, jogando o débito no mês
contábil errado. É defeito de fidelidade de dados, não de disponibilidade.

O `IllegalArgumentException` do `coerceIn` **não é atingível isoladamente** (`dueDate >= closingDate`
sempre vale por construção) — só via [1](#1).

### <a name="15"></a>15. Recorrente atrasado mais de um mês desaparece

`GetPendingRecurringUseCase.kt:15-25`

O filtro de "tratadas" e o corte de dia usam exclusivamente `today.yearMonth`. Não existe backlog.
Verificado que não há outro caminho: o único consumidor é o dashboard
(`DashboardComponentsBuilder.kt:105-109`) e o único ponto que abre a confirmação é o card pendente;
`RecurringScreen` lista recorrências, não ocorrências.

**Repro:** recorrência de dia 10 não confirmada em junho. Em 01/07 ela some da lista; ao reaparecer
em 10/07 é a ocorrência de **julho** (`ConfirmRecurringUseCase.kt:50`). Junho fica perdido, sem
registro. Paliativo indireto: o date picker da confirmação não tem `minDate`, então dá para
retroagir a data — ao custo de julho ficar pendente.

### <a name="16"></a>16. Valor negativo aceito no formulário de transação

`TransactionForm.kt:60` · `BuildTransactionUseCaseImpl.kt:40` · `MoneyInputTransformation.kt:20-46`

Ambas as validações exigem apenas `moneyToDouble() != 0.0`, nunca `> 0`. E a
`MoneyInputTransformation` — o **único** filtro do campo, já que `keyboardOptions` é só dica de IME
— preserva o sinal **deliberadamente** (`isNegative = text.startsWith("-")` … `"-$formatted"`).

Cadeia até o ledger confirmada: `LedgerEntryWriter.kt:180-187` faz `EXPENSE -> -cents`, logo
`-(-5000) = +5000` na conta ASSET. `Σ = 0` passa, nada rejeita. Grava uma **despesa que aumenta o
saldo**, com a perna nominal EXPENSE negativa contaminando categoria, orçamento e relatório.

Caminhos reais: **colar** `-50` no campo vazio; no Desktop, digitar dígitos + `Home` + `-`.
Digitar `-` primeiro **não** funciona (`digitsOnly.isEmpty()` limpa o campo) — a auditoria original
errou nesse ponto.

**Severidade rebaixada para Médio:** o sinal fica visível no campo (`-R$ 0,50`) e não há caminho
acidental. A transferência é imune (`TransferBetweenAccountsUseCase.kt:37` — `ensure(amount > 0.0)`).

### <a name="17"></a>17. "Sem categoria" some do detalhamento e distorce os percentuais

`CalculateReportCategorySpendingUseCase.kt:77-89`

`totals.mapNotNull { categoriesByDimension[dimensionId] ?: return@mapNotNull null }` descarta a
chave `null`, que o contrato de `IEntryRepository.totalsByDimension` define explicitamente como
"the unclassified total". O `total` é somado **depois** do descarte, e o percentual é calculado
sobre esse total menor. Enquanto isso `scopeStats` inclui o não classificado.

Alcançável: `BuildTransactionUseCaseImpl.kt:47-49` exige `title` **ou** `category` — categoria é
opcional.

**Repro:** no mês, R$ 100 com categoria e R$ 100 sem → o `ReportContextCard` mostra
"Despesas R$ 200"; "Gastos por categoria" lista um único item de R$ 100 com **100%**.

*(A auditoria original atribuiu o total ao `CategorySpendingCard`, que na verdade não exibe total
nenhum — o total vem do `ReportContextCard`. A substância do defeito não muda.)*

### <a name="18"></a>18. Percentual de categoria pode passar de 100% ou ficar negativo

`CalculateCategorySpendingUseCaseImpl.kt:33` · `CalculateReportCategorySpendingUseCase.kt:88` ·
`CategorySpendingCard.kt:112`

`if (total > 0) (amount / total) * 100 else 0.0`, e o card faz
`progress = { (spending.percentage / 100).toFloat() }` **sem `coerceIn`**. Duas falhas:

- `total <= 0` → **todos** os percentuais viram `0.0` e todas as barras esvaziam, embora os valores
  continuem exibidos ao lado. Estado incoerente em vez de ausente.
- item de sinal oposto — **alcançável**, pela mesma porta do [16](#16), e `EntryDao.kt:311`
  documenta o caso (*"a refund on an expense reads as income by the same rule"*). Com A=+300 e
  B=−100, `total=200` → A = **150%**, B = **−50%**.

### <a name="19"></a>19. Índice posicional de fatura muda sozinho

`InvoiceTransactionsViewModel.kt:55,107,208-218` · `InvoiceDao.kt:19` (`ORDER BY openingMonth DESC`)

A seleção é **posição** sobre uma lista viva; qualquer fatura de mês posterior entra na posição 0 e
desloca as demais. Contraste com `CreditCardsViewModel.kt:43-52`, que guarda **identidade**
(`selectedCardId`) e recalcula o índice.

**Repro:** com a fatura aberta selecionada, lançar um parcelamento em 6x → 5 faturas `FUTURE` de
meses futuros entram no topo → a tela passa a exibir outra fatura, com outros lançamentos e outros
botões (fechar/pagar), sem que o usuário tenha navegado.

### <a name="20"></a>20. `BudgetRepository.insert/update` não são atômicos

`BudgetRepository.kt:47-60`

`update` faz `dao.update` → `deleteBudgetCategories` → N `insertBudgetCategory`, tudo solto — a
classe sequer recebe `AppDatabase` no construtor. Falha ou cancelamento entre o delete e os inserts
deixa o orçamento **sem nenhuma categoria**, e um orçamento sem categorias reporta `spent = 0.0`
para sempre, silenciosamente.

`CategoryRepository.kt:69-76,83-92,109-116` envolve cada operação composta em
`useWriterConnection { immediateTransaction { … } }` — o padrão correto já existe no projeto.

### <a name="21"></a>21. N+1 de queries no dashboard e no cálculo por categoria

- `DashboardComponentsBuilder.kt:218-230` — `entryRepository.balance(account.id)` dentro de um
  `map` sobre todas as contas.
- `CalculateCategorySpendingUseCaseImpl.kt:22-23` — `dimensionBalanceInMonth` por categoria;
  `IEntryRepository.kt:200-203` é literalmente
  `dimensionIds.distinct().associateWith { dimensionBalanceInMonth(month, it) }`, e o próprio KDoc
  o chama de *"a thin fan"*. Também afeta `CalculateBudgetProgressUseCase.kt:41`.

`DashboardViewModel.kt:87-97` combina **9** fontes; qualquer emissão reexecuta o builder inteiro,
incluindo os dois N+1. O comentário em `:73-78` já reconhece a aridade como teto.

**Agravante:** a API agregada **já existe** — `totalsByDimension`/`totalsByDimensionInScope`
(`EntryDao.kt:361-377,426-443`) são um único `GROUP BY`. Falta um `balancesByAccount()` equivalente.

### <a name="22"></a>22. Todo o trabalho de agregação roda na main thread

`grep -rn "flowOn"` sobre `feature/`, `core/` e `app/` retorna **zero ocorrências**.

O bloco de transformação do `combine` executa no contexto do coletor, e o coletor é a corrotina de
sharing de `stateIn(viewModelScope, …)` → `Dispatchers.Main.immediate`. As chamadas de Room saltam
internamente para o dispatcher do banco, mas o trabalho Kotlin puro **não**:

- `DashboardComponentsBuilder.kt:380-396` — `recents()` faz `sortedByDescending` sobre a lista
  **inteira** de transações antes de `take(n)`;
- `ReportViewerViewModel.kt:201` — `sortedByDescending{}.groupBy{}`;
- `BudgetsViewModel.kt:29-53`, `DashboardViewModel.kt:87-143`.

### <a name="23"></a>23. Leituras de detalhe carregam a tabela `entries` inteira

`TransactionRepository.kt:88-100,115-121`

`mapToDomain()` combina com `entryDao.observeAll()`, que é `SELECT * FROM entries ORDER BY id ASC`,
**sem filtro** — mesmo o fluxo filtrado (`observeTransactionsBy`) puxa todas as entries. E
`observeTransactionById(id)` é derivado de `observeAllTransactions()` + `firstOrNull`, consumido
por telas de detalhe reais (`ViewTransactionViewModel.kt:31`, `ViewAdjustmentViewModel.kt:28`).

Abrir um modal de detalhe materializa N transações × M entries + o plano de contas, e re-executa a
cada escrita no ledger (o `distinctUntilChanged` corta a emissão, não a leitura).

**As alternativas existem e estão sem uso em produção**, ambas confirmadas por grep:
`TransactionDao.kt:21-22` `observeById(id)` — zero chamadas, nem em teste;
`EntryDao.kt:152-155` `observeEntriesWithAccountByTransactionId` — só usada em fakes de teste.

### <a name="24"></a>24. Round-trip monetário quebra em locales sem 2 casas

`CurrencyFormatter.{android,jvm,ios}.kt` · `MoneyFormatter.kt:5-7` · `EditTransactionModal.kt:88` ·
`EditAccountBalanceModal.kt:88,235-240`

O `expect` **não tem parâmetro de moeda**; as três implementações usam o locale default do
dispositivo. O parser, por sua vez, conta dígitos e **assume 2 casas**. Nenhum locale é fixado no
projeto (sem `resConfigs`/`localeConfig`).

Com JPY/KRW (0 casas), `format(12.34)` → `"￥12"` → parse `0.12`: cada abertura da modal de edição
divide o valor por 100. Com KWD (3 casas) o erro é ×10. Pior no `EditAccountBalanceModal`, onde o
botão fica **habilitado** (`0.12 != 12.34`) e salvar grava o valor errado.

Relacionado: a `currency` persistida por `Entry` no ledger nunca chega à formatação — limitação de
API, hoje sem impacto por ser monomoeda (ver [Refutados](#refutados), L5).

### <a name="25"></a>25. Nome de conta: `isEmpty()` e trim assimétrico

`ValidateAccountNameUseCase.kt:16,24,34` · `CreateAccountUseCase.kt:33` · `AccountFormViewModel.kt:130`

`isEmpty()` não considera `"   "`; a criação faz `name.trim()` → grava conta de **nome vazio**.

**Correção à auditoria original:** na criação **não** há duplicata com espaço — `:34` já compara
com `name.trim()`. O furo está na **edição**: o use case retorna `name.right()` sem trim (`:24`) e
o ViewModel grava esse valor cru. Renomear para `"Nubank "` persiste com espaço; criar depois
`"Nubank"` passa na checagem → duas contas homônimas, exatamente o que o comentário em `:27-30`
diz que não pode existir.

### <a name="26"></a>26. Limite disponível ignora faturas `RETROACTIVE`

`CalculateAvailableLimitUseCase.kt:14-18` · `InvoiceDao.kt:34`
(`status NOT IN ('PAID','RETROACTIVE')`)

Verificado o que `RETROACTIVE` significa aqui antes de julgar: é fatura de ciclo passado criada sob
demanda que carrega **dívida real** e é explicitamente pagável — `Invoice.isPayable` a inclui
(`Invoice.kt:37-41`) e `CloseInvoiceUseCase.kt:58-63` documenta que ela *"closes like any other,
and is paid explicitly"*. É dívida não paga fora da soma de não pagas: o limite disponível exibido
fica maior que o real.

### <a name="27"></a>27. Navegação para issue sem `launchSingleTop`

`SupportGraph.kt:25` — `navController.navigate(SupportIssueRoute(issueId))` sem opções e sem guarda
de clique. Em janela compacta, dois toques rápidos no card durante a animação empilham duas
entradas idênticas, exigindo dois "voltar". (Em extra-wide o clique vai para o detail pane.)

### <a name="28"></a>28. Mês destacado em qualquer ano no picker

`MonthPickerDropdownMenu.kt:87-92,175` — passa `selectedYearMonth.month` sem o ano, e
`isSelected = month == selectedMonth`. Com Mar/2026 selecionado, navegar para 2025 dentro do menu
mantém MAR pintado como selecionado.

### <a name="29"></a>29. 8 strings sem tradução em `values-en`

Diff real das chaves: `values/` = **655**, `values-en/` = **647**. Faltam exatamente estas 8, nem
uma a mais:

`categories_empty_filter` · `categories_filter_active` · `categories_filter_archived` ·
`category_card_archived` · `retire_error_has_budget` · `retire_error_has_recurring` ·
`retire_error_has_transactions` · `view_category_unarchive`

Nenhuma chave existe só em `values-en/`. Usuário em inglês vê essas 8 em português.

### <a name="30"></a>30. Filtro do Support produz lista vazia sem estado vazio

`SupportViewModel.kt:31-40` — `Empty` só é emitido quando a lista **não filtrada** está vazia; o
filtro é aplicado depois, dentro de `Content`. Com apenas issues ativas, trocar o filtro para
"inativas" renderiza só o card de resumo sobre um vazio inexplicado.

*(A auditoria original também alegou que o card mostra o total não filtrado — **falso**:
`SupportUiState.kt:14-18` deriva `waitingSupportCount` e `issues.size` da lista já filtrada.)*

---

## Baixos

### <a name="31"></a>31. Fatura com saldo negativo não fecha e não explica
`CloseInvoiceUseCase.kt:54-56` — `ensure(invoiceAmount >= 0)`. O botão "Fechar fatura" é oferecido
sem consultar o valor. **Existe saída** (o `EditInvoiceBalanceModal`, em `CreditCardsScreen.kt:212`
e `InvoiceTransactionsScreen.kt:262-268`) — o que falta é a mensagem: combinado com [13](#13), o
usuário clica, nada acontece e nada explica que é preciso ajustar o saldo antes.

### <a name="32"></a>32. Faturas `FUTURE` órfãs quando um parcelamento falha
`AddInstallmentUseCaseImpl.kt:104-115` vs `:147-151` — cada fatura é criada em sua própria
transação; o rollback desfaz apenas o installment. Parcelar em 12x num cartão com 3 faturas cria 9
`FUTURE`; se `createTransactions` falhar, as 9 permanecem vazias para o usuário apagar à mão.

### <a name="33"></a>33. Janela de recorrentes próximos não atravessa o mês
`DashboardComponentsBuilder.kt:357-364` — `effectiveDay > input.today.day` com `effectiveDay`
sempre calculado sobre o mês corrente. Em 28/06 com `daysAhead = 7`, um recorrente do dia 3 nunca
aparece; ele só reaparece em 01/07, já como pendente.

### <a name="34"></a>34. `contentDescription = null` em botões de navegação
`SupportScreen.kt:75-79` e `:105-108` (FAB) · `SupportIssueScreen.kt:72-76` ·
`MonthSelector.kt:52-56,97-101` · `MonthPickerDropdownMenu.kt:120-125,135-140` ·
`InvoiceMonthNavigator.kt:56-66,69-79`. Em todos, o `Icon` é o único conteúdo do clicável — o
leitor de tela anuncia apenas "botão". O FAB do shell (`ChromeHost.kt:248`) faz certo.

### <a name="35"></a>35. Resposta do Support: texto limpo antes de confirmar
`SupportIssueViewModel.kt:70-86` — `replyText.value = ""` na linha 75, **antes** do
`addSupportReplyUseCase`; o ramo de falha não restaura o texto nem notifica. Offline, o usuário
perde a mensagem digitada sem aviso.

### <a name="36"></a>36. `startKoin` dentro de `MainViewController()` no iOS
`app/ios/.../MainViewController.kt:9-17` — sem `GlobalContext.getOrNull()`, `stopKoin()` ou flag.
`ContentView.swift:5-8` chama uma vez por instância de `ComposeView`. Android
(`AndroidApp.onCreate`) e Desktop (`remember` no `application`) iniciam uma vez por processo.
Repro exige segunda cena (iPad multi-window) ou recriação da `WindowGroup` — não determinístico;
o fato é a ausência de guarda.

### <a name="37"></a>37. Rótulo de perspectiva do relatório engana quando a conta some
`ReportViewerViewModel.kt:129-135` — `accountIds` vazio significa legitimamente "todas" no resto do
arquivo, então o fallback está correto nesse caso. O defeito é o segundo: se todos os ids
selecionados deixaram de existir, o rótulo diz "todas as contas" enquanto os números vêm de um
escopo inexistente (zeros). Alcançabilidade baixa — `observeAllAccountsIncludingClosed()` significa
que arquivar não dispara, só exclusão definitiva com relatório aberto.

### <a name="38"></a>38. Achados menores agrupados

- **Sem `hasBudgetForRecurring`** (`IBudgetRepository.kt:6-13`) — a assimetria que habilita
  [6](#6). A regra existe e é aplicada para categoria (`ResolveCategoryRetirabilityUseCase.kt:25`).
- **`DeleteBudgetViewModel.kt:19-23`** — sem `Either`, sem crashlytics, sem `showError`; único fora
  do padrão da área. Exceção do Room aqui segue a mecânica de [3](#3).
- **`SetDefaultAccountUseCase.kt:10-20`** — N updates fora de transação. "Dois defaults" é
  **refutado** como estado permanente (é transitório); o que resta é uma emissão intermediária com
  **zero** defaults, e persistência desse estado se houver falha no meio.
- **`CalculateBudgetProgressUseCase.kt:49-52`** — `firstOrNull()` entre confirmações do mesmo mês.
  A ordem **não** é arbitrária (`TransactionDao.kt:24` ordena `date DESC, id DESC`); o defeito é de
  acoplamento — depende de uma ordenação que só existe por acidente do DAO.
- **`DeleteCategoryUseCase.kt:23-27`** — TOCTOU entre `resolveRetirability` e `delete`. Janela
  **benigna**: `entries.dimensionId` é `SET NULL` e `budget_categories` é CASCADE — perda de
  classificação, não corrupção.
- **`FormattingLocalsHost.kt:39-63`** — `DateFormats` (sem `equals`) recriado sem `remember`,
  invalidando todos os leitores de `LocalDateFormats`. "A cada recomposição" **superestima**: o
  host só recompõe em troca de configuração/locale. Erro real, frequência baixa, custo alto quando
  dispara.
- **`AccountsViewModel.kt:174-175`** — indexação sem `getOrNull`. A premissa de listas
  dessincronizadas é **falsa** (`getAllAccounts()` e `observeAllAccounts()` são a mesma query) e a
  lista vazia é inalcançável; sobra uma janela estreita sem repro determinístico.
- **`TransferBetweenAccountsViewModel.kt:37`** — fallback silencioso da conta de origem. Na prática
  **código morto**: não há como arquivar a conta com o bottom sheet aberto por cima. E a troca
  aparece na tela (só não é anunciada).
- **`MoneyFormatter.kt:6` / `MoneyInputTransformation.kt:29`** — `toLongOrNull() ?: 0L` zera o campo
  com ≥19 dígitos. Robustez, não bug de usuário.
- **`MainActivity.kt:29-33`** — `AppAndroidPreview` chama `App()`, que faz `koinInject`; o preview
  do IDE sempre quebra. Nenhum impacto em runtime.
- **`ChromeHost.kt:133-135`** — FAB sem guarda de reentrância. Janela de 1-2 frames até o scrim
  subir; classe de defeito real, sem repro confiável.
- **`ChromeHost.kt:106-111`** — troca de aba sem `saveState`/`restoreState` descarta filtros e
  scroll. **Aparentemente deliberado**: o comentário em `:104-105` declara "hosts never stack".
- **Sem plurais** (`strings.xml:129-130`) — `"%1$d issues no total"` produz "1 issues no total".
- **`SupportIssueScreen.kt:54-60`** — coleta de eventos sem consciência de ciclo de vida; o resto da
  tela usa `collectAsStateWithLifecycle`.
- **`ModalManager`/`DetailPaneController` como `single` de app** — não há vazamento
  (`dismissAll()` também chama `onDismissed`, e todo caminho de remoção passa por lá). O que
  procede é o design: modais não são atrelados ao ciclo de navegação, obrigando workarounds
  pontuais como `SupportScreen.kt:57-61`.
- **`CategoryFormViewModel`** — `type` não persistido na edição. **Correto como está**: o seletor
  não é renderizado em modo edição e a imutabilidade do tipo é coerente com o `CLAUDE.md`
  (`Category.type` é declaração do usuário). No máximo falta um comentário.

---

## <a name="refutados"></a>Refutados na verificação

Nove achados da varredura inicial **não procedem**. Registrados aqui para não voltarem.

| ID | Achado alegado | Por que não procede |
|---|---|---|
| CC1 | `getInvoiceById` lançaria `NoSuchElementException`, tornando 6 `ensureNotNull` código morto | **Código já corrigido.** `InvoiceRepository.kt:206-218` é leitura one-shot que retorna `null`; `observeInvoiceById` usa `flatMapLatest` emitindo `flowOf(null)`, não `emptyFlow()`. Commit `9b1f05201 "Fix(Invoice): answer 'not there' when the invoice is gone"` |
| DS1 | Dashboard e tela de cartões escolheriam faturas diferentes | As duas escolhem a **mesma**, por desenho documentado. `CreditCardsViewModel.kt:165-167`: *"Mirrors the previous associateBy over the DESC-ordered unpaid list (last wins)"* — o `minByOrNull` foi escrito para replicar o `associateBy`. Resta só fragilidade de manutenção no lado do dashboard |
| L5 | `Σ = 0 por moeda` seria vacuosa por gravar `BASE_CURRENCY` fixo | `TransactionLeg` **não tem campo de moeda** — agrupar por moeda ali é impossível, não omitido. Nenhum caminho cria conta não-BRL (não há campo na UI). Monomoeda é decisão documentada em `Currency.kt:4-10` |
| L9 | `ensureSystemAccount` duplicaria contas de sistema por corrida | Os **três (e únicos)** chamadores de `writeEntries` estão dentro de `useWriterConnection`, que serializa os escritores. Sem repro. Índice único seria hardening |
| AC11 | Arquivar conta deveria checar recorrências como a exclusão faz | Não é guarda esquecida: a de `delete` existe porque a FK é `SET_NULL`. Arquivar não remove linha, o vínculo sobrevive e é reversível. Há teste do contrato (`RetireAccountGuardsTest`: *"delete and close are different actions"*) e os consumidores tratam `isArchived` explicitamente |
| AC13 | `dayMonthYear.parse` cru no `onClick` | O `enabled` usa `runCatching` sobre o **mesmo** texto (`TransferBetweenAccountsModal.kt:198-202`); o botão não fica habilitado com data inválida |
| UI11 | `ChromeEffect.reset()` apagaria a config da tela entrante | Existe **um único** consumidor da API (`DashboardScreen.kt:37`) — a premissa de duas telas em transição não se sustenta. E o que ele publica leva ao mesmo `Default` do reset. Latente, sem sintoma |
| UI13 | `dismiss()` sem argumento fecharia o modal errado | Cada modal é um `ModalBottomSheet` com scrim: um modal inferior não é clicável, então `lastOrNull()` é sempre o que recebeu o toque. Varredura de todas as chamadas não achou caminho contrário |
| UI20 | `isOnScreen` compararia dp contra pixels | No Compose Desktop **`Dp` é a unidade AWT**: `Windows.desktop.kt:120` faz `setLocation(position.x.value.roundToInt(), …)` sem densidade, e compara contra `graphicsConfiguration.bounds` do mesmo jeito. Mesmo espaço de coordenadas |

---

## Áreas verificadas sem achados

Descartadas explicitamente durante a varredura, com leitura do código:

- **Migrações Room** — as sete estão registradas em ordem e cobrem 1→10 sem lacuna (o salto 7→9 é
  deliberado). `MIGRATION_9_10` verifica balanço antes e depois, mais `verifyNoOrphanDimensions` e
  `PRAGMA foreign_key_check`, abortando dentro de `migrate()` para o Room reverter.
- **As duas portas do ledger** — `TransactionRemovalHook.onRemoved` roda dentro do mesmo
  `immediateTransaction`, e a transação é lida inteira antes do `deleteById`.
- **FKs e `onDelete`** — `entries` → `transactions` CASCADE, → `accounts` NO ACTION, → `dimensions`
  SET NULL; os três índices presentes.
- **Datas de fatura em meses curtos / bissexto / virada de ano** — `YearMonth.safeOnDay`/
  `effectiveDay` fazem `coerceAtMost(numberOfDays)` e são dono único da regra.
- **Fronteira de mês / timezone** — `Transaction.date` é `LocalDate`; não há conversão de instante
  nas queries. `LedgerConverters` grava ISO-8601, então `substr(date,1,7)` é comparação lexical
  correta.
- **Dupla contagem cartão↔orçamento** — não ocorre: a compra tem a dimensão na perna nominal, o
  pagamento é `LIABILITY → ASSET` sem dimensão de categoria.
- **Transferência: mesma conta, valor zero, para cartão** — cobertas em três camadas; o
  `AccountSelector` só recebe contas `ASSET` não arquivadas.
- **Pernas órfãs após edição / mudança receita↔despesa** — `updateTransaction` reescreve todas as
  pernas na mesma transação; `TransactionForm.from` normaliza os campos por tipo.
- **Excluir parcela do meio / parcelamento cruzando fatura paga / reabrir fatura paga / excluir
  cartão com parcelas** — todos recusados ou reconciliados corretamente, com mensagem.
- **Unicidade de nome de categoria** — cobre arquivadas, com teste dedicado.
- **Listas Compose sem `key`** — todas as auditadas têm `key` estável.
- **`AdaptiveDetail`** — limpeza dos `ViewModelStore` correta nos dois sentidos do redimensionamento.

---

## Não verificados

Levantados na varredura e **não** submetidos à passagem adversarial — tratar como suspeitas, não
como fatos:

- `DashboardViewModel.kt:134-138` — modo de edição não suspende o recálculo do modo de visualização
  (`editing ?: viewing` mantém o `viewingState` coletado).
- `ReportViewerViewModel.kt:201` — ordem indeterminada dentro de um mesmo dia (falta desempate por
  `id`).
- `ReportViewerViewModel.kt:65-70,97` — relatório de fatura degrada para relatório de período se as
  faturas escolhidas somem.
- `TransactionsViewModel.kt:54,76-87` — mês e filtros aplicados em memória sobre
  `observeAllTransactions()`, sem paginação (relacionado a [23](#23); a infra
  `TransactionDao.observeBy` já existe).
- `InvoiceRepository.kt:59` — `creditCard!!` em `observeInvoicesByCreditCard`, NPE na janela de
  corrida entre os dois flows do `combine`. *(Encontrado durante a verificação de CC1, ao refutá-lo
  — a linha original alegada estava errada; esta não foi verificada.)*

---

## Padrões recorrentes

Cinco fios ligam a maioria dos achados confirmados e valem mais que os itens isolados:

1. **Sem trava de submissão** ([4](#4)) — 8 ViewModels de escrita, nenhum com `isSubmitting`.
   Uma única correção de padrão fecha todos.
2. **Erro engolido** ([13](#13)) — `.onLeft { crashlytics.recordException(it) }` sem `showError`,
   em 5 pontos. O projeto já tem a correção escrita e comentada em um deles.
3. **Sem `.catch` antes de `stateIn`** ([3](#3)) — 6 pipelines; no Android, mata o processo.
4. **Escritas compostas fora de transação** ([20](#20), [32](#32), [38](#38)) — o padrão correto
   (`useWriterConnection { immediateTransaction { … } }`) já existe em `CategoryRepository` e no
   `TransactionRepository`; falta propagá-lo.
5. **Agregação ingênua** ([21](#21), [22](#22), [23](#23)) — N+1 onde a API agregada já existe,
   `SELECT *` sem filtro, e zero `flowOn` no projeto inteiro.

Sugestão de ordem de ataque, por relação esforço/impacto: o pacote transversal (1–3) primeiro, que
é mecânico e elimina uma classe inteira de falhas; depois [1](#1) e [8](#8), que produzem estados
irrecuperáveis; depois [5](#5), [6](#6) e [7](#7), que corrompem números.
