# Etapa 14 — Eventos tipados

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Introduzir `abstract class Event` na camada de domínio e refatorar a interface `Analytics` para aceitar eventos tipados em vez de `name: String, params: Map<String, String>`.

Centralizar todos os eventos em objetos/classes no pacote `domain/analytics/event/`, agrupados por domínio. Atualizar todas as implementações da interface e todos os ViewModels para usar os novos tipos.

### Regras

- Eventos **sem parâmetros** → `object` (ex: `object CloseInvoice : Event(name = "close_invoice")`)
- Eventos **com parâmetros** → `class` com secondary constructor que recebe o modelo de domínio relevante
- A lógica de mapeamento (modelo → params do evento) fica no evento, **não** no ViewModel
- O ViewModel só chama `analytics.logEvent(Transactions.CreateTransaction(operation))`

---

## Arquivos afetados

**Novos — domain:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/Event.kt` — `abstract class Event(val name: String, val params: Map<String, String> = emptyMap())`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Transactions.kt` — `CreateTransaction`, `EditTransaction`, `DeleteTransaction`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Installments.kt` — `CreateInstallments`, `DeleteInstallments`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Accounts.kt` — `CreateAccount`, `EditAccount`, `DeleteAccount`, `AdjustAccountBalance`, `TransferBetweenAccounts`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/CreditCards.kt` — `CreateCreditCard`, `EditCreditCard`, `DeleteCreditCard`, `CloseInvoice`, `PayInvoice`, `ReopenInvoice`, `DeleteFutureInvoice`, `AdvanceInvoicePayment`, `AdjustInvoiceBalance`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Budgets.kt` — `CreateBudget`, `EditBudget`, `DeleteBudget`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Recurring.kt` — `CreateRecurring`, `EditRecurring`, `DeleteRecurring`, `ConfirmRecurring`, `SkipRecurring`, `StopRecurring`, `ReactivateRecurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Categories.kt` — `CreateCategory`, `EditCategory`, `DeleteCategory`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Dashboard.kt` — `EnterDashboardEditMode`, `SaveDashboardLayout`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Reports.kt` — `GenerateReport`, `ShareReport`, `PrintReport`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Support.kt` — `CreateSupportIssue`, `SendSupportReply`

**Modificados — domain:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/Analytics.kt` — substituir `logEvent(name: String, params: Map<String, String>)` por `logEvent(event: Event)`

**Modificados — platform:**
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/analytics/FirebaseAnalyticsImpl.kt` — implementar `logEvent(event: Event)`
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/analytics/FirebaseAnalyticsImpl.kt` — implementar `logEvent(event: Event)`
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/analytics/NoOpAnalytics.kt` — implementar `logEvent(event: Event)`

**Modificados — ui (31 ViewModels):**
- `ui/modal/addTransaction/AddTransactionViewModel.kt`
- `ui/modal/editTransaction/EditTransactionViewModel.kt`
- `ui/modal/deleteTransaction/DeleteTransactionViewModel.kt`
- `ui/modal/addInstallment/AddInstallmentViewModel.kt`
- `ui/modal/deleteInstallment/DeleteInstallmentViewModel.kt`
- `ui/modal/accountForm/AccountFormViewModel.kt`
- `ui/modal/deleteAccount/DeleteAccountViewModel.kt`
- `ui/modal/editAccountBalance/EditAccountBalanceViewModel.kt`
- `ui/modal/transferBetweenAccounts/TransferBetweenAccountsViewModel.kt`
- `ui/modal/creditCardForm/CreditCardFormViewModel.kt`
- `ui/modal/deleteCreditCard/DeleteCreditCardViewModel.kt`
- `ui/modal/closeInvoice/CloseInvoiceViewModel.kt`
- `ui/modal/payInvoice/PayInvoiceViewModel.kt`
- `ui/modal/reopenInvoice/ReopenInvoiceViewModel.kt`
- `ui/modal/deleteFutureInvoice/DeleteFutureInvoiceViewModel.kt`
- `ui/modal/advancePayment/AdvancePaymentViewModel.kt`
- `ui/modal/editInvoiceBalance/EditInvoiceBalanceViewModel.kt`
- `ui/modal/budgetForm/BudgetFormViewModel.kt`
- `ui/modal/deleteBudget/DeleteBudgetViewModel.kt`
- `ui/modal/recurringForm/RecurringFormViewModel.kt`
- `ui/modal/deleteRecurring/DeleteRecurringViewModel.kt`
- `ui/modal/confirmRecurring/ConfirmRecurringViewModel.kt`
- `ui/modal/stopRecurring/StopRecurringViewModel.kt`
- `ui/modal/reactivateRecurring/ReactivateRecurringViewModel.kt`
- `ui/modal/categoryForm/CategoryFormViewModel.kt`
- `ui/modal/deleteCategory/DeleteCategoryViewModel.kt`
- `ui/screen/dashboard/DashboardViewModel.kt`
- `ui/screen/report/config/ReportConfigViewModel.kt`
- `ui/screen/report/viewer/ReportViewerViewModel.kt`
- `ui/screen/support/SupportViewModel.kt`
- `ui/screen/support/SupportIssueViewModel.kt`

---

## Critério de aceite

**Validação manual:**
1. Compilar o projeto sem erros — nenhuma chamada `logEvent(name = "...", params = ...)` deve restar
2. Firebase DebugView (Android): disparar qualquer evento (ex: criar transação) → confirmar que o evento chega com nome e params corretos

**Revisão de código:**
- [ ] `abstract class Event` sem dependências externas — fica em `domain/analytics/`
- [ ] Eventos sem params declarados como `object`, eventos com params como `class`
- [ ] Secondary constructors nos eventos que recebem modelos de domínio — mapeamento fora dos ViewModels
- [ ] `FirebaseAnalyticsImpl` usa `event.name` e `event.params` — sem lógica adicional
- [ ] Nenhum ViewModel monta `buildMap` ou passa string literal de nome de evento
- [ ] `logEvent(name: String, params: Map<String, String>)` removido da interface e de todas as implementações

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.