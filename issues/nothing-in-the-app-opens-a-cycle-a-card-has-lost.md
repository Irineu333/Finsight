---
area: creditcards
severity: medium
type: data
---

# Nada no app abre o ciclo que um cartão perdeu

## Invariante

Todo cartão ativo tem exatamente uma fatura `OPEN`, e o app sabe restaurá-la quando ela falta.

A segunda metade é falsa hoje, e a primeira não tem dono: `Invoice.Status.OPEN` só é gravado por
`OpenInvoiceUseCase` (e por `ReopenInvoiceUseCase`, que **exige** uma `OPEN` já existente como
sucessora e por isso não consegue criar a primeira). `OpenInvoiceUseCase` tem dois chamadores, e
os dois podem terminar sem fatura aberta sem desfazer nada — um deles nem olha o resultado.

## Mecânica

**Quem pode perder a fatura aberta.** `CloseInvoiceUseCase.invoke()` grava a fatura como `CLOSED`
e então chama `openInvoiceUseCase(creditCardId, openingMonth = invoice.closingMonth)` **sem
`bind()`**: o `Either` é descartado dentro do `either {}`. Se `OpenInvoiceUseCase` recusar —
`OverlappingInvoice`, `CreditCardNotFound`, ou um erro de banco no `invoiceRepository.insert` — o
fechamento devolve `Right`, o `CloseInvoiceViewModel` trata como sucesso, e o cartão fica com o
ciclo fechado e nenhum aberto.

`AddCreditCardUseCase.invoke()` grava o cartão e a sua conta `LIABILITY` numa transação de escrita
própria (`CreditCardRepository.insert()`, um `useWriterConnection { immediateTransaction { … } }`)
e só depois faz `openInvoiceUseCase(...).bind()`. Uma falha ali devolve `Left` **com o cartão já
persistido** — e o KDoc do próprio use case afirma o contrário: "Opening it is part of the
creation and not a follow-up to it: a card whose invoice failed to open would accept no expense at
all, so the failure fails the creation rather than being reported as success." A falha falha o
*relato*, não a criação; o estado que o KDoc descreve como inaceitável é o que fica no banco.

**Por que não há volta.** `CreateInvoiceUseCase.invoke()` — a única outra porta para uma fatura
nascer — começa com `invoices.find { it.status.isOpen } ?: raise(InvoiceException(InvoiceError.NoOpenInvoice))`,
e nunca produz `OPEN` (o próprio KDoc: "this operation never produces `Invoice.Status.OPEN`"). Sem
uma `OPEN`, portanto: `GetOrCreateInvoiceForMonthUseCaseImpl` falha em qualquer mês que ainda não
tenha fatura — o caminho de **toda** despesa de cartão, de todo parcelamento e de toda confirmação
de recorrência; o `CreateInvoiceModal` também não resolve, porque passa pelo mesmo
`CreateInvoiceUseCase`; e não existe afordância "abrir fatura" em tela alguma.

Um cartão com movimento não pode nem ser excluído (`DeleteCreditCardUseCase` recusa com
`HAS_TRANSACTIONS`) nem arquivado enquanto dever (`ArchiveCreditCardUseCase` →
`ArchiveAccountUseCaseImpl`, `HAS_BALANCE`).

**Quem falha aberto nesse estado.** `EditInvoiceBalanceViewModel.onAction()`, no
`SelectCreditCard`, grava o cartão **antes** de saber a fatura e desiste no meio quando não há:

```kotlin
selectedCreditCard.value = action.creditCard
selectedInvoice.value = invoiceRepository.getOpenInvoice(action.creditCard.id) ?: return@launch
```

O `return@launch` deixa `selectedInvoice` apontando para a fatura do cartão **anterior**, e nada
reconcilia os dois. A folha denomina o valor pela moeda do cartão novo sobre o saldo da fatura
velha, o seletor exibe um item que não está na própria lista, e `submit()` manda
`selectedInvoice.value` para `AdjustInvoiceUseCase`, que posta a perna `ADJUSTMENT` na conta
`LIABILITY` do **cartão anterior**. Mesmo com fatura aberta a divergência existe enquanto o
`getOpenInvoice` suspende, porque o cartão já foi trocado. A folha irmã faz o oposto e diz por
quê: `InvoicePaymentViewModel.selectCreditCard()` limpa a fatura **antes** de assumir o cartão,
"so that no pair of the new card with the old card's invoice is ever observed".

## Evidência

- `feature/creditcards/impl/.../usecase/CloseInvoiceUseCase.kt` — `openInvoiceUseCase(...)` sem
  `bind()`, dentro do `either {}`
- `feature/creditcards/impl/.../usecase/AddCreditCardUseCase.kt` — o KDoc, e a sequência
  `repository.insert(...)` `.bind()` → `openInvoiceUseCase(...).bind()`
- `feature/creditcards/impl/.../repository/CreditCardRepository.kt` — `insert()`, uma unidade de
  trabalho própria
- `feature/creditcards/impl/.../usecase/OpenInvoiceUseCase.kt` — as recusas `CreditCardNotFound`
  e `OverlappingInvoice`
- `feature/creditcards/impl/.../usecase/CreateInvoiceUseCase.kt` — o
  `?: raise(InvoiceException(InvoiceError.NoOpenInvoice))` e o KDoc "never produces OPEN"
- `feature/creditcards/impl/.../usecase/ReopenInvoiceUseCase.kt` — o
  `ensureNotNull(successor?.takeIf { it.status == Invoice.Status.OPEN })`, que impede reabrir para
  criar a primeira
- `grep -rn "Status.OPEN"` em produção — só `OpenInvoiceUseCase` e `ReopenInvoiceUseCase` escrevem
  esse status
- `feature/transactions/impl/.../usecase/BuildTransactionUseCaseImpl.kt`,
  `feature/creditcards/impl/.../usecase/AddInstallmentUseCaseImpl.kt` e
  `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — os três chamadores de
  `GetOrCreateInvoiceForMonthUseCase`
- `feature/creditcards/impl/.../editInvoiceBalance/EditInvoiceBalanceViewModel.kt` — `onAction()`,
  ramo `SelectCreditCard`, com o `?: return@launch`; e `submit()`
- `feature/creditcards/impl/.../usecase/AdjustInvoiceUseCase.kt` — a perna em
  `invoice.creditCard.accountId`
- contraste: `feature/creditcards/impl/.../invoicePayment/InvoicePaymentViewModel.kt` —
  `selectCreditCard()`, que limpa a fatura primeiro, com o KDoc que explica a regra

## Consequência

O cartão vira um cartão morto: nenhuma despesa nova entra num mês que ainda não tem fatura,
nenhuma tela abre um ciclo, e o cartão não pode ser excluído nem arquivado enquanto tiver
movimento ou saldo. A saída é editar o banco. E, enquanto ele está nesse estado, a folha de
"editar saldo da fatura" grava o ajuste na fatura de outro cartão.

*Não verificado: não foi possível produzir a recusa de `OpenInvoiceUseCase` por um caminho de
usuário — os índices únicos de `invoices` tornam `OverlappingInvoice` difícil de alcançar, e sobra
a falha de banco. O que está verificado é que, uma vez no estado, não há volta.*

## Sugestão

Três costuras independentes: dar `bind()` ao `openInvoiceUseCase` de `CloseInvoiceUseCase` (ou
desfazer o fechamento se ele falhar); pôr a criação do cartão e a abertura da primeira fatura na
mesma unidade de trabalho, como o KDoc já promete; e, no `EditInvoiceBalanceViewModel`, limpar a
fatura antes de assumir o cartão, como a folha de pagamento faz. Não vinculante.
