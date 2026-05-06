# `:feature:dashboard`

Feature terminal — sem `:api`. Exibe a tela inicial do app com cards de saldo, cartões de crédito, lista de contas e widgets configuráveis.

## Módulos

- `:feature:dashboard:ui` — `DashboardEntry` (ponto de extensão consumido pela navegação) e composables compartilháveis com outras features.
- `:feature:dashboard:impl` — implementação completa.

## Responsabilidade

Hub financeiro: visão geral do mês, atalhos para ações rápidas, widgets de spending/income por categoria.

## Implementação (`:impl`)

- **Tela:** `DashboardScreen` + `DashboardViewModel` + `DashboardUiState`.
- **Use cases:** `BuildDashboardViewingUseCase`, `CalculateCategoryIncomeUseCase`, `CalculateCategorySpendingUseCase`.
- **Repositório:** preferências de widgets (`DashboardPreferencesRepository`).
- **Constantes:** componentes ativos, ações rápidas, breakdowns income/expense.
- **Eventos analytics e modais** específicos da home financeira.

## Dependências

- `:ui`: `:core:ui`, `:core:utils`, `:feature:transactions:api`.
- `:impl`: `:ui`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:accounts:api`, `:feature:accounts:ui`, `:feature:creditCards:api`, `:feature:creditCards:ui`, `:feature:recurring:api`, `:feature:budgets:api`, `:feature:budgets:ui`, `:feature:home:api`, `:core:database`, `:core:ui`, `:core:platform`, `:core:analytics`, `:core:utils`.
