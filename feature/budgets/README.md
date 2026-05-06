# `:feature:budgets`

## Responsabilidade

Definir orçamentos por categoria (limite fixo ou percentual de uma recorrência) e calcular progresso/saldo restante.

## Módulos

- `:feature:budgets:api`
- `:feature:budgets:ui`
- `:feature:budgets:impl`

## Contratos públicos (`:api`)

- **Modelos:** `Budget` (id, title, categoryIds, **iconKey: String**, amount, limitType, percentage, recurringId, createdAt), `Budget.LimitType` (`FIXED`, `PERCENTAGE`), `BudgetProgress` (budget, spent, recurringLabel, recurring, progress, remaining, isExceeded).
- **Repositórios:** `IBudgetRepository`.
- **Use cases:** `ICalculateBudgetProgressUseCase`.

> `iconKey` é `String` (mesma regra de `Category` e `Budget`): nada de Compose no `:api`.

## UI compartilhada (`:ui`)

- `BudgetProgressCard`.

## Implementação (`:impl`)

- **Tela:** `BudgetsScreen` + `BudgetsViewModel`.
- **Modais:** `BudgetFormModal` + `BudgetFormViewModel`, `DeleteBudgetModal`.
- **Repositório:** `BudgetRepository`.
- **Use cases:** `CalculateBudgetProgressUseCase`.

## Dependências

- `:api`: `:feature:categories:api`, `:feature:recurring:api`, `:core:utils`.
- `:ui`: `:api`, `:core:ui`.
- `:impl`: `:api`, `:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:recurring:api`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:accounts:ui`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
