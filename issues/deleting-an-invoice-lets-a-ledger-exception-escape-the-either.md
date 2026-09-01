---
area: creditcards
severity: high
type: crash
---

# Excluir uma fatura deixa a recusa do razão escapar do `Either` e derrubar o app

## Cenário

**DADO** uma fatura `RETROACTIVE` que recebeu uma antecipação de pagamento da conta "Nubank",
e a conta "Nubank" zerada e arquivada depois disso
**QUANDO** o usuário toca em "Excluir Fatura" e confirma
**ENTÃO** a `ClosedAccountException` sobe pelo `either {}` até o `viewModelScope.launch` do
`DeleteFutureInvoiceViewModel` e o processo morre
**DEVERIA** voltar como `Left`, como a assinatura `Either<InvoiceException, Unit>` promete

## Mecânica

`DeleteFutureInvoiceUseCase` remove as transações da fatura com
`transactionRepository.deleteTransactionById(...)` dentro do `either {}`. Essa chamada
**lança**: `removeRow()` passa por `ensureClosedAccountsKeepTheirBalance()`, que faz
`throw ClosedAccountException` quando alguma perna toca conta permanente arquivada — e
`closedLegBlockingChange()` é `firstOrNull { it.account.isArchived && it.account.type.isPermanent }`.
Arrow não intercepta `throw`, só `Raise`; o KDoc de `PayInvoicePaymentUseCase` estabelece
isso e o resolve com `catch{}.bind()`, este caminho não.

A antecipação é o que põe uma perna `ASSET` dentro da fatura: `AdvanceInvoicePaymentUseCase`
grava a perna da conta e a perna do cartão, e é a do cartão que carrega
`invoice.dimensionId` — a mesma dimensão pela qual o use case varre as transações a apagar.

Há um segundo defeito no mesmo laço: as N remoções são N transações de banco separadas,
porque `deleteTransactionById` abre a sua própria. Uma falha no meio deixa a fatura
parcialmente esvaziada e ainda existente. `deleteTransactionsByIds(ids)` já é atômico e
está sem uso aqui.

## Evidência

- `feature/creditcards/impl/.../usecase/DeleteFutureInvoiceUseCase.kt` — o
  `forEach { transactionRepository.deleteTransactionById(...) }` dentro do `either {}`, sem `catch`
- `core/ledger/.../repository/TransactionRepository.kt` — `deleteTransactionById()` →
  `removeRow()` → `ensureClosedAccountsKeepTheirBalance()`, que lança; e
  `deleteTransactionsByIds()`, um `immediateTransaction` só
- `core/ledger/.../extension/Ledger.kt` — `closedLegBlockingChange()`, o predicado que decide
- `feature/creditcards/impl/.../deleteFutureInvoice/DeleteFutureInvoiceViewModel.kt` —
  `deleteInvoice()`: `viewModelScope.launch` sem `catch`, o `onLeft` só alcança o `Left`
- `feature/creditcards/impl/.../usecase/AdvanceInvoicePaymentUseCase.kt` — a perna `ASSET`
  e a perna do cartão com `dimensionId = invoice.dimensionId`
- contraste: `feature/creditcards/impl/.../usecase/PayInvoicePaymentUseCase.kt` — o KDoc que
  estabelece que `either {}` não pega `throw`

## Consequência

O app fecha. E como a exclusão não é uma unidade de trabalho, uma interrupção no meio do
laço deixa parte dos lançamentos apagados sob uma fatura que continua lá.

## Sugestão

Trocar o laço por `deleteTransactionsByIds(...)` — que resolve a atomicidade — e embrulhar a
chamada em `catch {}.bind()`, como o irmão já faz. Não vinculante.
