# `:feature:transactions`

## Responsabilidade

Núcleo financeiro do app: registrar movimentações (`EXPENSE`, `INCOME`, `ADJUSTMENT`), agrupar em operações (transação simples, pagamento, transferência) e calcular saldos/estatísticas.

## Módulos

- `:feature:transactions:api`
- `:feature:transactions:ui`
- `:feature:transactions:impl`

## Contratos públicos (`:api`)

- **Modelos:** `Transaction` (id, operationId, type, amount, title, date, categoryId, target, creditCardId, invoiceId, accountId), `Transaction.Type` (`EXPENSE`, `INCOME`, `ADJUSTMENT`), `Operation` (id, kind, title, date, recurring, installment, transactions), `Operation.Kind` (`TRANSACTION`, `PAYMENT`, `TRANSFER`), `OperationInstallment`, `OperationRecurring`, `OperationPerspective`, `TransactionForm` (input validado).
- **Repositórios:** `ITransactionRepository` (CRUD + observe com filtros), `IOperationRepository`.
- **Use cases:** `IBuildTransactionUseCase` (`TransactionForm → Either<Throwable, Transaction>`), `ICalculateBalanceUseCase` (`YearMonth → Double`), `ICalculateTransactionStatsUseCase`.

## UI compartilhada (`:ui`)

- `TransactionsEntry` (entry de tela), `OperationCard`, `TargetSelector`, `IOperationUiMapper`.

## Implementação (`:impl`)

- **Tela:** `TransactionsScreen` + `TransactionsViewModel`.
- **Modais:** `AddTransactionModal` + `AddTransactionViewModel`, `EditTransactionModal`, `DeleteTransactionModal`, `ViewOperationModal`, `ViewAdjustmentModal`.
- **Repositórios:** `TransactionRepository`, `OperationRepository`.
- **Use cases:** `BuildTransactionUseCase`, `CalculateBalanceUseCase`, `CalculateTransactionStatsUseCase`.

## Dependências

- `:api`: `:feature:accounts:api`, `:feature:categories:api`, `:feature:creditCards:api`, `:core:utils`.
- `:ui`: `:api`, `:core:ui`.
- `:impl`: `:api`, `:ui`, `:feature:accounts:api`, `:feature:categories:api`, `:feature:creditCards:api`, `:feature:creditCards:ui`, `:feature:installments:api`, `:feature:recurring:api`, `:feature:home:api`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
