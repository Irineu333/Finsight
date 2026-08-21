---
area: recurring
severity: low
type: ux
---

# A folha de confirmação deixa data e fatura divergirem, sem corrigir nem avisar

## Cenário

**DADO** uma confirmação de recorrência no cartão
**QUANDO** o usuário muda a data — ou a fatura — de modo que a data caia fora da janela da
fatura selecionada
**ENTÃO** a compra é gravada assim mesmo, sem correção e sem aviso
**DEVERIA** fazer uma das duas coisas, como fazem as duas telas irmãs que escrevem no cartão

## Mecânica

`DateChanged` não mexe na fatura e `InvoiceSelected` não mexe na data. A fatura padrão é "a
primeira `OPEN`", escolhida sem olhar para a data.

As duas telas vizinhas já resolveram, cada uma de um jeito legítimo:
`AddTransactionViewModel.placeDateInInvoiceWindow()` reprojeta a **data** na janela da
fatura, e `AddInstallmentUiState.isDateOutsideInvoice` existe para **dizer** — pela
`InvoiceMonthSelection.diverges(...)`, que é a regra pronta.

## Evidência

- `feature/recurring/impl/.../confirmRecurring/ConfirmRecurringViewModel.kt` — `onAction()`,
  ramos `DateChanged` e `InvoiceSelected`; e o `init`, que define
  `selectedInvoice = allInvoices.firstOrNull { it.status.isOpen } ?: allInvoices.firstOrNull()`
- `feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt` —
  `placeDateInInvoiceWindow()`, a solução por correção
- `feature/creditcards/impl/.../addInstallment/AddInstallmentUiState.kt` —
  `isDateOutsideInvoice = invoiceSelection?.diverges(form.date) == true`, a solução por aviso
- `feature/creditcards/impl/.../addInstallment/AddInstallmentModal.kt` — o `supportingText`
  que a renderiza

## Consequência

Compra datada de um ciclo aparecendo na fatura de outro, sem sinal para o usuário.

## Sugestão

Reusar `InvoiceMonthSelection.diverges(...)` no estado do modal e mostrar o mesmo aviso do
parcelamento. Não vinculante.
