## Why

O app oferece hoje **três** ajustes de saldo que são o mesmo ajuste: "Editar Saldo Atual",
"Editar Saldo Final" e "Editar Saldo Inicial". A distinção não existe no domínio —
`AdjustFinalBalanceUseCase` e `AdjustOpeningBalanceUseCase` não fazem nada além de projetar uma
data e delegar a `AdjustBalanceUseCase(targetBalance, adjustmentDate, account)`, que é a operação
real e já recebe a data. O usuário escolhe entre conceitos onde havia apenas um calendário.

O preço dessa fachada é concreto:

- **O "saldo inicial" mostra um número e grava outro.** O modal pré-preenche com o saldo até o
  mês alvo (`EditAccountBalanceViewModel.kt:51-59`), enquanto a gravação ocorre no último dia do
  mês anterior e o `AdjustBalanceUseCase.kt:29-37` recalcula a base naquele mês. Editar o saldo
  inicial de março exibe o saldo de 31/março e aplica a diferença sobre o de 28/fevereiro — o
  valor no campo e o rótulo de diferença divergem do que é gravado.
- **`Type.CURRENT` é código morto**: a única referência fora do enum é o próprio `when` do
  ViewModel (`EditAccountBalanceViewModel.kt:109`); `AccountsScreen.kt:258-273` só abre `FINAL` e
  `INITIAL`.
- **O ajuste de fatura não tem data.** Ele grava sempre em hoje
  (`EditInvoiceBalanceViewModel.kt:119`), o que força uma única leitura do gesto — "esta fatura
  vale X, não sei por quê" — e torna impossível a outra, mais comum: "faltou um gasto nesta
  fatura", cuja ocorrência está dentro do ciclo.

A data de uma transação diz **quando ela ocorreu**; a fatura diz **onde ela se liquida**. São
eixos independentes, e o razão já os trata assim: o valor chega à fatura pela dimensão, e
`CalculateInvoiceUseCase` não tem corte de data. Faltava a interface admitir isso.

## What Changes

- **O tipo de ajuste deixa de existir.** Há uma operação — declarar o saldo que a conta (ou a
  fatura) deveria ter, numa data — e os pontos de entrada diferem apenas pela **data padrão**
  com que abrem o modal. `AdjustFinalBalanceUseCase`, `AdjustOpeningBalanceUseCase` e
  `EditAccountBalanceModal.Type` são apagados.
- **Os dois modais ganham campo de data**, com padrão por contexto e teto único em **hoje**:
  - "editar saldo inicial" abre no último dia do mês anterior;
  - "editar saldo final" abre no último dia do mês visível, limitado a hoje;
  - o ajuste de fatura abre na projeção de hoje na janela daquela fatura, e reprojeta ao trocar
    de fatura — a mesma hierarquia de `invoice-governs-date`, cujo escopo passa a incluí-lo.
- **O valor de referência passa a ser lido na data escolhida.** O pré-preenchimento e o rótulo
  de diferença deixam de derivar de um "mês alvo" paralelo à data, o que elimina por construção
  a divergência do saldo inicial.
- **A leitura escalar de saldo por conta ganha corte por data** em `:core:ledger`. A leitura
  mensal passa a derivar dela, sem uma segunda consulta.
- **O ajuste de fatura não é limitado pela janela da fatura.** Uma correção feita hoje sobre a
  fatura de janeiro é datada hoje; uma despesa esquecida é datada quando ocorreu. O teto é hoje
  e não há piso. A divergência entre data e janela continua sendo **dita, nunca corrigida**,
  reaproveitando `InvoiceMonthSelection.diverges` e a string existente.

**Fora de escopo, por decisão:** tornar a janela de compra uma invariante do razão. Hoje ela é
política de interface — `InvoiceWriteGuard.kt:30-41` verifica apenas o status da fatura, nunca a
data —, e continua sendo. Uma data fora da janela não corrompe número algum; o que a mudança faz
é não induzir a ela.

## Capabilities

### New Capabilities
- `balance-adjustment`: o ajuste de saldo — de conta e de fatura — como uma única operação, o
  valor-alvo declarado numa data escolhida pelo usuário. Cobre a ausência de tipos de ajuste, os
  padrões de data por ponto de entrada, o teto em hoje, a leitura do valor de referência na data,
  e a idempotência por data.

### Modified Capabilities
- `invoice-governs-date`: o escopo da hierarquia "cartão governa fatura, fatura governa data"
  passa a incluir o modal de ajuste de fatura, com a ressalva de que a natureza da perna decide o
  teto — um ajuste não é uma compra e por isso não tem o fechamento da fatura como limite.
- `ledger-reporting`: a leitura escalar do saldo de uma conta passa a admitir corte por **data**,
  e a leitura até um mês deriva dela.

## Impact

**`:core:ledger`**
- `EntryDao.balanceUpToMonth` (`EntryDao.kt:213-217`) — o corte `substr(o.date,1,7) <= :yearMonth`
  passa a `o.date <= :date`
- `IEntryRepository.accountBalanceUpTo` (`IEntryRepository.kt:136`) e sua implementação em
  `EntryRepository.kt:65`
- `CalculateBalanceUseCase.forAccount` ganha a sobrecarga por data; a mensal delega

**`:feature:accounts:impl`**
- Apagados: `AdjustFinalBalanceUseCase.kt`, `AdjustOpeningBalanceUseCase.kt` — sem testes
  próprios, referenciados apenas por `AccountsModule.kt:104-105` e pelo ViewModel
- `FutureMonthAdjustmentException` fica inalcançável quando o teto vira limite do seletor
- Nova projeção de data com nome, no domínio da feature (não na tela)
- `EditAccountBalanceModal` / `ViewModel` / `UiState` / `Action`: campo de data, base lida na
  data, `Type` removido, subtítulo de mês substituído pelo campo
- `AccountsScreen.kt:256-273`: os dois pontos de entrada passam a data padrão, não um tipo

**`:feature:creditcards:impl`**
- `EditInvoiceBalanceModal` / `ViewModel` / `UiState` / `Action`: campo de data, projeção na
  troca de fatura, teto em hoje, aviso de divergência via `InvoiceMonthSelection.diverges`

**Strings** — `values/` e `values-en/`, ambas: os três títulos viram um; a string do aviso
(`transaction_date_outside_invoice`) já existe e é reaproveitada.

**E2E** — `.maestro/flows/accounts/lifecycle.yaml` alcança o modal por `edit_account_balance_amount`
e `edit_account_balance_save`; as duas tags permanecem, mas o fluxo precisa ser reexecutado para
confirmar que o campo novo não desloca o alvo.

**Sem impacto:** boundary de escrita, migrações, `InvoiceWriteGuard`, o formato do lançamento de
ajuste no razão (contra-perna `EQUITY`), e as leituras por moeda, que seguem mensais.
