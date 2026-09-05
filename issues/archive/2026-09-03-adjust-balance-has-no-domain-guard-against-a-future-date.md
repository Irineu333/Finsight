---
area: accounts
severity: medium
type: data
verdict: fixed
---

# O ajuste de saldo não recusa data futura — a regra vive só no calendário

## Invariante

Um ajuste de saldo nunca é datado no futuro.

Hoje é falso no domínio: `AdjustBalanceUseCase` aceita qualquer `adjustmentDate` e grava a
transação de `ADJUSTMENT` nela. A única guarda do caso de uso é
`ensure(targetBalance != currentBalance)`.

Se uma data futura chegar lá, o formulário promete saldo alvo 200, a transação nasce em
2099, e o cartão da conta continua mostrando 140 — o ajuste "não fez nada".

## Mecânica

A feature já tem o dono dessa regra para o outro caminho de escrita:
`TransferBetweenAccountsUseCase` faz `ensure(date <= currentDate)` com
`TransferError.FutureDate`. O caminho do ajuste não tem equivalente — nem no caso de uso,
nem no ViewModel, nem numa função de validação.

O único freio é o `maxDate` passado ao `DatePickerModal`, e o KDoc do modal declara isso
como suficiente — *"An adjustment is never dated in the future, and the calendar is where
that is settled — not an error raised after the submit"* — enquanto o campo ao lado é
livremente digitável. É a regra de derivação invertida: a tela virou a guardiã do
invariante.

Abaixo não há rede: `LedgerEntryWriter` valida `Σ = 0`, encerramento de conta e pouso de
dimensão. Data não é assunto dele.

## Evidência

- `feature/accounts/impl/.../usecase/AdjustBalanceUseCase.kt` — `invoke()`: `adjustmentDate`
  entra direto no `TransactionIntent`; a única `ensure` é sobre o saldo
- `feature/accounts/impl/.../usecase/TransferBetweenAccountsUseCase.kt` —
  `ensure(date <= currentDate)`, a guarda que falta do outro lado
- `core/model/.../error/TransferError.kt` — `FutureDate` existe só para transferência
- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceModal.kt` —
  `maxDate = state.today` no `DatePickerModal`; o campo de texto acima não tem teto
- `core/ledger/.../repository/LedgerEntryWriter.kt` — `writeEntries()` não julga data

## Consequência

Um ajuste que o usuário lê como "corrigi meu saldo de hoje" pode aterrissar num mês que ele
nunca abre, sem erro nenhum.

**Alcance:** hoje o defeito é latente. Enquanto
`typing-in-the-adjustment-date-replaces-the-form-with-a-spinner` estiver aberto, só o
calendário — limitado — chega ao caso de uso. A falta da guarda vira alcançável no instante
em que aquele for corrigido.

## Sugestão

Mover a regra para `AdjustBalanceUseCase` com erro tipado próprio, e deixar o `maxDate`
como conveniência e não como guardião. Não vinculante.

## Desfecho

**Causa real** — a descrita, e confirmada no disco: `AdjustBalanceUseCaseImpl.invoke()` ia da
resolução da conta (`ensureNotNull(...) { AccountException(NOT_FOUND) }`) direto para
`ensure(targetBalance != currentBalance)` e daí para o `TransactionIntent`, sem nada entre eles
que olhasse `adjustmentDate`. O caso de uso não recebia relógio nenhum — não tinha como saber
que dia era hoje —, então a única coisa que impedia um ajuste futuro era o `maxDate = state.today`
do `DatePickerModal`. Regra derivável do domínio morando na tela, com o irmão da mesma feature
(`ValidateTransferUseCase.kt:62`, `ensure(date <= clock.today()) { TransferError.FutureDate }`)
fazendo o certo ao lado.

**Mudança** — o caso de uso passou a receber `clock: Clock` no construtor
(`feature/accounts/impl/.../usecase/AdjustBalanceUseCaseImpl.kt:36`) e a recusar a data futura
logo depois de resolver a conta e **antes** de ler o saldo:
`ensure(adjustmentDate <= clock.today()) { AccountException(AccountError.ADJUSTMENT_DATE_IN_FUTURE) }`
(`.../AdjustBalanceUseCaseImpl.kt:52-56`). Membro novo `AccountError.ADJUSTMENT_DATE_IN_FUTURE`
(`core/model/.../domain/error/AccountError.kt:67`, com KDoc dizendo por que a recusa existe),
ramo próprio no `toUiText()` exaustivo (`:80-81`) e a chave
`account_error_adjustment_date_in_future` nos dois `strings.xml` (pt `:23`, en `:22`).

O relógio foi **injetado** em vez de um validador concreto na `api` recebendo `today`: a
assinatura de `AdjustBalanceUseCase.invoke` não carrega `today`, então um validador só
funcionaria se cada chamador se lembrasse de passá-lo — devolvendo à tela e à ferramenta MCP a
decisão de *qual* regra vale, que é exatamente a inversão que este bug relata. Injetar não mexe
na assinatura (o duble `WorldAdjustBalance` do mundo de teste do MCP continua compilando) e o
Koin já publica um `Clock` — a mudança de produção é uma linha (`AccountsModule.kt:111`). Custo
total: 1 call site de produção e 11 construções em testes.

`EditAccountBalanceViewModel.toUiMessage()` ganhou o ramo `is AccountException -> error.toUiText()`:
sem ele o erro novo nasceria com tradução que ninguém lê, caindo no `ledger_action_error_generic`
como o resto de `AccountException`. É o mesmo defeito que
`a-refusal-with-its-own-message-still-arrives-as-the-generic-one.md` descreve como classe — aqui só
o ramo desta tela foi fechado, e aquele registro segue aberto para os demais.

O parâmetro `date` de `adjust_balance` (`feature/mcp/impl/.../tool/AccountOperationTools.kt:67-71`)
prometia só "Defaults to today"; agora diz que a data nunca é futura e por quê — um saldo é a soma
dos lançamentos até uma data, e corrigir uma data à frente de hoje corrige uma leitura que
ninguém pode tomar ainda.

**Prova** — teste novo `an adjustment dated after today is refused and nothing is written`
(`feature/accounts/impl/src/commonTest/.../AdjustBalanceUseCaseTest.kt:208`): relógio fixo em
2026-03-01, ajuste pedido para o dia seguinte; afirma `AccountError.ADJUSTMENT_DATE_IN_FUTURE`
e que o razão continua vazio. Antes da correção (na forma que compilava contra o construtor
antigo, pedindo 2099-01-01) ele ficou **vermelho** —
`java.lang.AssertionError at AdjustBalanceUseCaseTest.kt:186`, "a future adjustment was accepted".
Verde depois. Junto veio `an adjustment dated today is written` (`:231`), que fixa o limite como
inclusivo — hoje não é futuro. `AdjustBalanceUseCaseTest` fechou com `tests="9" failures="0"` e o
módulo inteiro com 108 testes em 21 classes, 0 falhas
(`./gradlew :feature:accounts:impl:testDebugUnitTest`). `:feature:mcp:impl:jvmTest --tests
"*OperationsFamilyOverTheProtocolTest*"` e a compilação de `:app:shared` e `:feature:mcp:impl`
seguem verdes.

**Commit** — `Fix(Domain): hold the three date rules the screens were holding alone`
