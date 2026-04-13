# Etapa 06 — Reportar exceções

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Refatorar todos os ViewModels (screens e modals) que chamam use cases retornando `Either` para reportar exceções via `crashlytics.recordException(it)` no `onLeft`. Também resolver todos os `// TODO: register exception` existentes no código e reportar exceções silenciadas que o ViewModel não tem acesso.

---

## Regras de reporte

- `Either.Left` com `Throwable` / `XxxException` → `crashlytics.recordException(it)` no ViewModel
- `Either.Left` com `XxxError` (validação) → **NÃO reportar** — é comportamento esperado
- Exceções silenciadas em repositórios (ex: `runCatching { }.getOrNull()`) → reportar no próprio repositório com Crashlytics injetado

---

## Arquivos afetados

### ViewModels de modais (que chamam use cases de operação)

- `ui/modal/addTransaction/AddTransactionViewModel.kt` — resolve `// TODO: register exception`; reportar onLeft de `buildTransactionUseCase` e `addInstallmentUseCase`
- `ui/modal/creditCardForm/CreditCardFormViewModel.kt` — reportar onLeft de `addCreditCardUseCase` e `updateCreditCardUseCase`
- `ui/modal/closeInvoice/CloseInvoiceViewModel.kt` — adicionar `onLeft` com reporte
- `ui/modal/payInvoice/PayInvoiceViewModel.kt` — adicionar `onLeft` com reporte para `payInvoiceUseCase` e `payInvoicePaymentUseCase`
- `ui/modal/advancePayment/AdvancePaymentViewModel.kt` — adicionar `onLeft` com reporte
- `ui/modal/transferBetweenAccounts/TransferBetweenAccountsViewModel.kt` — adicionar `onLeft` com reporte
- `ui/modal/addInstallment/AddInstallmentViewModel.kt` — adicionar reporte
- `ui/modal/editAccountBalance/EditAccountBalanceViewModel.kt` — adicionar `onLeft` para `adjustInitialBalanceUseCase` e `adjustFinalBalanceUseCase` (refatorados na etapa 05)
- `ui/modal/editInvoiceBalance/EditInvoiceBalanceViewModel.kt` — adicionar `onLeft` para `adjustInvoiceUseCase`
- `ui/modal/editTransaction/EditTransactionViewModel.kt` — adicionar `onLeft` com reporte
- `ui/modal/accountForm/AccountFormViewModel.kt` — reportar onLeft de `createAccountUseCase` e `updateAccountUseCase`
- `ui/modal/recurringForm/RecurringFormViewModel.kt` — reportar onLeft de `saveRecurringUseCase`
- `ui/modal/confirmRecurring/ConfirmRecurringViewModel.kt` — reportar onLeft de `confirmRecurringUseCase`

### ViewModels de screens

- `ui/screen/support/SupportViewModel.kt` — injetar Crashlytics; reportar onLeft de `createSupportIssueUseCase`
- `ui/screen/support/SupportIssueViewModel.kt` — injetar Crashlytics; reportar onLeft de `addSupportReplyUseCase`
- `ui/screen/dashboard/DashboardViewModel.kt` — reportar onLeft de `ensureDefaultAccountUseCase` (refatorado na etapa 05)

### ViewModels de delete

- `ui/modal/deleteAccount/DeleteAccountViewModel.kt` — reportar onLeft
- `ui/modal/deleteBudget/DeleteBudgetViewModel.kt` — reportar onLeft
- `ui/modal/deleteCategory/DeleteCategoryViewModel.kt` — reportar onLeft
- `ui/modal/deleteCreditCard/DeleteCreditCardViewModel.kt` — reportar onLeft
- `ui/modal/deleteFutureInvoice/DeleteFutureInvoiceViewModel.kt` — reportar onLeft
- `ui/modal/deleteInstallment/DeleteInstallmentViewModel.kt` — reportar onLeft
- `ui/modal/deleteRecurring/DeleteRecurringViewModel.kt` — reportar onLeft
- `ui/modal/deleteTransaction/DeleteTransactionViewModel.kt` — reportar onLeft

### ViewModels de recurring

- `ui/modal/reactivateRecurring/ReactivateRecurringViewModel.kt` — reportar onLeft
- `ui/modal/reopenInvoice/ReopenInvoiceViewModel.kt` — reportar onLeft
- `ui/modal/stopRecurring/StopRecurringViewModel.kt` — reportar onLeft

### Repositórios (exceções silenciadas)

- `database/repository/FirebaseSupportRepository.kt` — injetar `Crashlytics`; nos três `runCatching { }.getOrNull()` de deserialização, trocar por `.getOrElse { crashlytics.recordException(it); null }`

### DI

- Todos os ViewModels afetados precisam de `Crashlytics` injetado via construtor
- `FirebaseSupportRepository` precisa de `Crashlytics` injetado (já tem `Analytics`)
- Módulos Koin que registram ViewModels e repositórios devem passar o `get()` adicional

---

## Critério de aceite

**Validação manual:**
1. Forçar uma falha em operação com `recordException` → confirmar evento não-fatal no Firebase Console.
2. App continua funcionando normalmente após erros reportados.
3. Nenhum `// TODO: register exception` restante no código.

**Revisão de código:**
- [x] Todo ViewModel que chama use case com `Either<Throwable, ...>` tem `onLeft { crashlytics.recordException(it) }`
- [x] Validações (`Either<XxxError, ...>`) NÃO reportam exceções
- [x] `FirebaseSupportRepository` reporta exceções de deserialização silenciadas
- [x] `Crashlytics` injetado via construtor (não `koinInject`)
- [x] Nenhum `// TODO: register exception` restante

---

## Desvio

**Interface `Crashlytics.recordException`:** O plano assumia `Exception`, mas Arrow's `catch {}` retorna `Either<Throwable, ...>`. Para evitar casts desnecessários em todos os ViewModels, o parâmetro foi alterado para `Throwable` na interface e nas três implementações (`FirebaseCrashlyticsImpl` Android, iOS e `NoOpCrashlytics`). O SDK gitlive também aceita `Throwable`. Impacto: nenhum nas etapas seguintes.

**`CategoriesViewModel`:** Não estava listado no plano, mas continha `// TODO: register exception` para `createDefaultCategories()` que retorna `Either<Throwable, Unit>`. Crashlytics foi injetado e o TODO resolvido. Impacto: nenhum.

**`DeleteBudget/Category/Installment/Recurring/Transaction` ViewModels:** O plano listou esses 5 ViewModels como "reportar onLeft", mas nenhum deles usa `Either` — chamam métodos de repositório diretamente. Não foram modificados. Impacto: nenhum.
