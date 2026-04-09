# Etapa 02 — Screen views

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamada `logScreenView(screenName)` em cada tela principal listada na spec.

A chamada deve ser feita via `LaunchedEffect(Unit)` no composable da tela (não no ViewModel), para manter os ViewModels focados em estado e ações. `Analytics` é obtido via `koinInject()` ou passado como parâmetro do composable.

Telas e `screen_name` correspondentes (conforme spec):

| Composable | `screen_name` |
|---|---|
| `HomeScreen` → tab Dashboard | `dashboard` |
| `HomeScreen` → tab Transactions | `transactions` |
| `AccountsScreen` | `accounts` |
| `CreditCardsScreen` | `credit_cards` |
| `InvoiceTransactionsScreen` | `invoice_transactions` |
| `InstallmentsScreen` | `installments` |
| `BudgetsScreen` | `budgets` |
| `RecurringScreen` | `recurring` |
| `CategoriesScreen` | `categories` |
| `ReportConfigScreen` | `reports_config` |
| `ReportViewerScreen` | `reports_viewer` |
| `SupportScreen` | `support` |
| `SupportIssueScreen` | `support_issue` |

> Dashboard e Transactions fazem parte do `HomeScreen` com navegação por tabs. O `screen_view` deve ser disparado quando cada tab se torna visível (ao trocar de tab), não apenas na entrada inicial.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/home/HomeScreen.kt` — `LaunchedEffect` nas tabs Dashboard e Transactions
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/accounts/AccountsScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/creditCards/CreditCardsScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/invoiceTransactions/InvoiceTransactionsScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/installments/InstallmentsScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/budgets/BudgetsScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/recurring/RecurringScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/categories/CategoriesScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/report/config/ReportConfigScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/report/viewer/ReportViewerScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/support/SupportScreen.kt` — `LaunchedEffect`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/support/SupportIssueScreen.kt` — `LaunchedEffect`

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Abrir o app → confirmar `screen_view` com `screen_name: dashboard`.
2. Navegar para Transactions → confirmar `screen_view` com `screen_name: transactions`.
3. Navegar para cada tela principal e confirmar o `screen_name` correspondente.
4. Voltar ao Dashboard e trocar de tab → confirmar que `screen_view` é disparado novamente.

**Revisão de código:**
- [ ] Chamada feita via `LaunchedEffect(Unit)` (ou chave adequada para tab switching) — não no `init {}` do ViewModel
- [ ] `screen_name` segue exatamente os valores definidos na spec (`snake_case`, sem variações)
- [ ] Todas as 13 telas da spec estão cobertas

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
