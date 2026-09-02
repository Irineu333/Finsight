---
area: transversal
severity: medium
type: ux
---

# Uma escrita recusada não diz nada a quem a pediu

## Invariante

Toda recusa do domínio a uma ação que o usuário pediu chega até ele.

Hoje é falso em **14 lugares**: o `onLeft` grava a exceção no Crashlytics e termina. A
folha não fecha, nada aparece, e a ação simplesmente não acontece. Doze outros sítios
fazem o certo, com `modalManager.showError(...)` — a divisão é quase meio a meio, e é o
que torna isto um padrão e não um esquecimento.

Observável: uma fatura `OPEN` com saldo negativo — uma compra excluída depois de uma
antecipação — é recusada por `CloseInvoiceUseCase` com `NegativeBalance`. O usuário toca
"Fechar fatura", confirma, e o modal fica lá. Como a fatura não fecha, o ciclo seguinte
nunca abre, e nada na tela sugere que o caminho é "Editar saldo da fatura" primeiro.

## Mecânica

O projeto tem o padrão certo, tem o vocabulário (`toUiMessage()` / `toUiText()`) e tem o
canal (`ModalManager.showError`). O que falta é uso. O `ReopenInvoiceViewModel` documenta
a própria correção — *"Without this the sheet just did not close and said nothing"* — e
`InvoiceError.toUiText()` registra que *"Reopen is the first invoice flow to show its
refusal instead of failing silently"*: o código admite, por escrito, que os irmãos
continuam mudos.

## Evidência

Mudos, todos em resposta a uma ação do usuário:

- `feature/creditcards/impl/.../closeInvoice/CloseInvoiceViewModel.kt` — fechar fatura
- `feature/creditcards/impl/.../deleteFutureInvoice/DeleteFutureInvoiceViewModel.kt`
- `feature/creditcards/impl/.../creditCardForm/CreditCardFormViewModel.kt` — os dois ramos,
  criar e editar cartão
- `feature/creditcards/impl/.../viewCreditCard/ViewCreditCardViewModel.kt` — desarquivar
- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsViewModel.kt` —
  desarquivar cartão
- `feature/accounts/impl/.../accountForm/AccountFormViewModel.kt` — os dois ramos
- `feature/accounts/impl/.../viewAccount/ViewAccountViewModel.kt` — desarquivar
- `feature/categories/impl/.../viewCategory/ViewCategoryViewModel.kt` — desarquivar
- `feature/categories/impl/.../categories/CategoriesViewModel.kt` — criar categorias padrão
- `feature/recurring/impl/.../recurringForm/RecurringFormViewModel.kt`
- `feature/recurring/impl/.../unarchiveRecurring/UnarchiveRecurringViewModel.kt`
- `feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt`
- `feature/support/impl/.../support/SupportViewModel.kt` e `SupportIssueViewModel.kt`

O padrão correto, para comparação: `ReopenInvoiceViewModel`, `PayInvoiceViewModel`,
`AdvancePaymentViewModel`, `CreateInvoiceViewModel`, `EditInvoiceBalanceViewModel`,
`AddInstallmentViewModel`, `EditAccountBalanceViewModel`, `LaunchYieldViewModel`,
`TransferBetweenAccountsViewModel`, `ArchiveRecurringViewModel`,
`ConfirmRecurringViewModel`, `SkipRecurringViewModel`.

Fora da conta, por ser legítimo: `DashboardViewModel` (`ensureDefaultAccountUseCase` no
`init`) não responde a pedido nenhum e não tem a quem avisar.

## Consequência

Um botão que não faz nada é indistinguível de um app travado. O usuário não descobre a
condição que o domínio recusou, e a recusa mais cara — fechar fatura — bloqueia o ciclo
inteiro do cartão sem uma palavra.

## Sugestão

O `toUiMessage()` de `ReopenInvoiceViewModel` é o molde. Vale checar, ao aplicar, se o erro
tem `UiText.Res` próprio: `InvoiceError.NegativeBalance` hoje cai no genérico. Não
vinculante.
