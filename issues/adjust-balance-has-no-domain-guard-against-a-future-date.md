---
area: accounts
severity: medium
type: data
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
