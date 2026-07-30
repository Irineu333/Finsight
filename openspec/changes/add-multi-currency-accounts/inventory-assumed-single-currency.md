# Inventário da tarefa 1.2 — sítios que passam a moeda explicitamente

Saída da tarefa 1.1. Cada sítio abaixo teve de informar uma moeda que **não é derivável ali**:
porque a figura agrega entre contas, ou porque a fachada que a origina ainda não carrega moeda.

O registro é **derivado, não mantido à mão**: todo sítio usa a constante marcadora
`ASSUMED_SINGLE_CURRENCY` (`:core:model`), então a lista é

```bash
grep -rn --include='*.kt' ASSUMED_SINGLE_CURRENCY . | grep -v '/build/'
```

**Critério de pronto da tarefa 8.10:** apagar a constante e compilar. Se compilar, a lista está
vazia. É gate mecânico, não auditoria — foi por isso que a constante existe em vez de só um
documento.

Os sítios que **não** entram nesta lista são os que já resolveram a moeda para sempre, lendo-a da
`Account` ou da `Entry` que já tinham à mão: `AccountsViewModel`, `AccountsScreen`,
`ArchiveAccountModal`, `EditAccountBalanceModal`, `TransferBetweenAccountsModal`,
`ReportConfigScreen` (o `AccountCard`), `DashboardComponentsBuilder`, `TransactionUiMapper`,
`ViewTransactionUiState`, `ViewAdjustmentUiState` e `EditTransactionModal`.

## Por que cada grupo ainda não sabe, e quem o fecha

### A. Figura que agrega entre contas → fechada por **3.11–3.14** + **6.8**
A leitura passa a devolver saldo por moeda, a consolidação reduz à base resolvida, e a exatidão
passa a ser derivada. Só então a moeda desta figura tem resposta.

| arquivo | figura |
|---|---|
| `feature/transactions/.../BalanceOverviewFactory.kt` | as 3 visões do resumo (contas, cartões, geral) |
| `feature/transactions/.../TransactionsUiState.kt` | os defaults de estado vazio das mesmas 3 |
| `feature/dashboard/.../DashboardComponentContent.kt` | `TotalBalanceCard`, os 8 `BalanceCard` de fluxo, os 2 `CategorySpendingCard`, o `BudgetProgressCard` |
| `feature/report/.../ReportViewerViewModel.kt` | `Stats.Account` e `Stats.Invoice` |
| `feature/report/.../ReportViewerScreen.kt` | os 2 `CategorySpendingCard` |
| `feature/categories/.../ViewCategoryModal.kt` | total gasto da categoria |

### B. Valor de fachada de **cartão** → fechada por **7.2/7.3** + **9.7**
O cartão passa a ter moeda quando `CreditCardFormModal` a escolhe e `CreditCardRepository` a grava.

**A metade de *leitura* saiu em 7.8:** `CreditCard` ganhou uma `currency` **espelhada** da sua
linha `LIABILITY`, pelo mesmo veículo do `isArchived` — o `JOIN` que o `CreditCardDao` já fazia
passou a projetar `a.currency`. A escolha e a escrita continuam sendo de 9.7; o espelho não
escolhe nada, e por isso a inversão de 9.8 (exatamente dois sítios escolhem moeda) segue de pé.
Os sítios abaixo que tinham o cartão à mão fecharam com isso; os que ainda não têm — os de
**entrada** e o **formulário** — esperam 9.7.

| arquivo | figura | estado |
|---|---|---|
| `feature/creditcards/.../InvoiceUiMapperImpl.kt` | as figuras da fatura na modal | **fechado (7.8)** |
| `feature/creditcards/.../InvoiceTransactionsViewModel.kt` | as 4 figuras de cada fatura | **fechado (7.8)** |
| `feature/creditcards/.../AddInstallmentModal.kt`, `feature/transactions/.../AddTransactionModal.kt` | contador de parcelas | **fechado (7.8)** |
| `feature/creditcards/.../CreditCardsScreen.kt`, `.../DashboardComponentContent.kt`, `.../ReportConfigScreen.kt` | `CreditCardCard(currency = …)` | 9.7 |
| `feature/creditcards/.../CreditCardFormViewModel.kt`, `.../CreditCardFormModal.kt` | limite do cartão (exibição e entrada) | 9.7 |
| `feature/creditcards/.../ViewCreditCardModal.kt`, `.../ArchivedCreditCardCard.kt` | limite do cartão | 9.7 |
| `feature/creditcards/.../ArchiveCreditCardModal.kt` | saldo que bloqueia o arquivamento | 9.7 |
| `feature/creditcards/.../PayInvoiceModal.kt`, `.../AdvancePaymentModal.kt`, `.../EditInvoiceBalanceModal.kt` | devido, valor pago, ajuste | 9.7 |
| `feature/creditcards/.../InstallmentsScreen.kt` | parcelamento (denominado pelo cartão) | 9.7 |
| `feature/transactions/.../ViewTransactionModal.kt` | total do parcelamento | 9.7 |
| `feature/transactions/.../AddTransactionModal.kt` | entrada de valor | 9.7 |

### C. Valor de fachada de **recorrência** → fechada por **9.4**
| arquivo | figura |
|---|---|
| `feature/recurring/.../RecurringScreen.kt`, `.../ViewRecurringModal.kt`, `.../RecurringFormModal.kt`, `.../ConfirmRecurringModal.kt` | valor da recorrência |
| `feature/dashboard/.../DashboardComponentContent.kt` | valor da recorrência no widget |

### D. Limite de **orçamento** → fechada por **8.5–8.7**
A entidade de orçamento ganha a moeda do limite (coluna criada em 4.2).

| arquivo | figura |
|---|---|
| `feature/budgets/.../BudgetFormModal.kt`, `.../BudgetFormViewModel.kt` | entrada e exibição do limite |
| `feature/budgets/.../ViewBudgetModal.kt`, `.../BudgetsScreen.kt` | limite, gasto, restante, excedido |

### E. Documento exportado → fechada por **8.1/8.2**
| arquivo | figura |
|---|---|
| `feature/report/.../ReportExportLayout.kt` | valor de cada linha de categoria do relatório |

### F. Previews → fechada por **6.8**
| arquivo | figura |
|---|---|
| `feature/dashboard/.../DashboardPreviewFactory.kt` | as contas de exemplo |
