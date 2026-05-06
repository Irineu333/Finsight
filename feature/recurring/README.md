# `:feature:recurring`

## Responsabilidade

Definir transações recorrentes (income/expense mensais) e gerenciar suas ocorrências (confirmar, pular, parar, reativar).

## Módulos

- `:feature:recurring:api`
- `:feature:recurring:impl`

> Sem `:ui`: composables próprios ficam em `:impl`. Outras features consomem o `:api` via Koin (modal entries) sem importar UI.

## Contratos públicos (`:api`)

- **Modelos:** `Recurring` (id, **type: Recurring.Type**, amount, title, dayOfMonth, categoryId, accountId, creditCardId, createdAt, isActive), `Recurring.Type` (`INCOME`, `EXPENSE` — enum próprio, sem `ADJUSTMENT`), `RecurringOccurrence` (id, recurringId, cycleNumber, yearMonth, status, operationId, effectiveDate, handledAt), `RecurringOccurrence.Status` (`CONFIRMED`, `SKIPPED`).
- **Repositórios:** `IRecurringRepository`, `IRecurringOccurrenceRepository`.
- **Use cases:** `IGetPendingRecurringUseCase`.
- **Mappers:** `IRecurringMapper`.
- **Modal entries (interfaces):** `RecurringFormModalEntry`, `ViewRecurringModalEntry`, `ConfirmRecurringModalEntry`.

> `Recurring.Type` é independente de `Transaction.Type`. A conversão acontece apenas no mapper de persistência (`RecurringMapper`), evitando ciclo entre `:feature:recurring:api` e `:feature:transactions:api`.

## Implementação (`:impl`)

- **Tela:** `RecurringScreen` + `RecurringViewModel` (UiState/Action).
- **Modais:** implementações de `RecurringFormModalEntryImpl`, `ViewRecurringModalEntryImpl`, `ConfirmRecurringModalEntryImpl`.
- **Repositórios:** `RecurringRepository`, `RecurringOccurrenceRepository`.
- **Use cases:** `GetPendingRecurringUseCase`, `SaveRecurringUseCase`, `ConfirmRecurringUseCase`, `SkipRecurringUseCase`, `StopRecurringUseCase`, `ReactivateRecurringUseCase`.
- **Form state:** `RecurringForm`.
- **Mappers:** `RecurringMapper`, `RecurringOccurrenceMapper`.

## Dependências

- `:api`: `:core:utils`.
- `:impl`: `:api`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:accounts:api`, `:feature:accounts:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:creditCards:api`, `:feature:creditCards:ui`, `:feature:home:api`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
