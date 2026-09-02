---
area: transversal
severity: high
type: concurrency
---

# Nenhuma escrita recusa o segundo toque enquanto a primeira ainda está em voo

## Invariante

Um botão que dispara uma escrita para de aceitar toques até que ela termine.

Hoje é falso em toda escrita menos uma: `LaunchYieldViewModel` é o único que guarda
`isSubmitting` e o único cujo botão consulta `!state.isSubmitting`. Nos demais o `submit()`
é um `viewModelScope.launch` incondicional, e o `dismiss()` só acontece no `onRight` — depois
da gravação, quando o segundo toque já entrou.

## Mecânica

Todo caso de uso de escrita lê o estado, decide, e só então grava; leitura e decisão não
estão na mesma transação de banco que a escrita, então duas corrotinas passam pela mesma
guarda antes de qualquer uma gravar.

**Antecipação de pagamento**, o pior caso: `AdvanceInvoicePaymentUseCase` lê o devido com
`calculateInvoiceUseCase(invoice)` e checa `ensure(amount <= currentBillAmount)` **fora** do
`catch { transactionRepository.createTransaction(...) }`, que é onde a transação de banco
começa. Fatura devendo R$ 100, dois toques rápidos → ambas as corrotinas passam pela guarda
antes de qualquer escrita → duas transações de R$ 100 → fatura com saldo −100.

O `InvoiceWriteGuard` não cobre isso: ele veta escrita em fatura `PAID`, e o status só muda
depois da gravação.

O contraexemplo é a confirmação de recorrência, e ela mostra o que basta:
`RecurringOccurrenceRepository.confirmCycle()` põe a leitura, o `require` de reentrada e as
duas escritas dentro de um `useWriterConnection { immediateTransaction { … } }`, e o toque
duplo ali não duplica nada — sem que o ViewModel tenha trava alguma.

## Evidência

Sem trava, com o botão que os dispara:

- `feature/creditcards/impl/.../payInvoice/PayInvoiceViewModel.kt` — `submit()`;
  `PayInvoiceModal.kt`, `enabled` só olha data, conta e valor
- `feature/creditcards/impl/.../advancePayment/AdvancePaymentViewModel.kt` — `submit()`
- `feature/creditcards/impl/.../closeInvoice/CloseInvoiceViewModel.kt` — `closeInvoice()`;
  `CloseInvoiceModal.kt`, botão **sem `enabled`**
- `feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt` — `submit()`
- `feature/transactions/impl/.../editTransaction/EditTransactionViewModel.kt` — `submit()`
- `feature/accounts/impl/.../transferBetweenAccounts/TransferBetweenAccountsViewModel.kt` — `submit()`
- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceViewModel.kt` — `submit()`
- `feature/shell/impl/.../home/ChromeHost.kt` — `AddTransactionFab(onClick = onAddTransaction)`,
  sem guarda de reentrância

O padrão correto, já escrito: `feature/accounts/impl/.../launchYield/LaunchYieldViewModel.kt`
(`if (isSubmitting.value) return@launch`) e `LaunchYieldModal.kt`
(`enabled = yieldAmount > 0.0 && !state.isSubmitting`).

## Consequência

Lançamento em duplicidade em toda a superfície de escrita, com saldo, fatura e orçamento
errados. No caso da fatura o resultado é uma dívida negativa, que `CloseInvoiceUseCase`
recusa fechar — o cartão trava até que alguém ajuste o saldo à mão.

## Sugestão

`LaunchYieldViewModel` é o molde e já está no projeto: `isSubmitting` no estado, o `return`
no início do `submit`, o `enabled` no botão. Não vinculante.
