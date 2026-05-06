# `:feature:installments`

## Responsabilidade

Dividir uma transação em parcelas (installments) e rastrear o total/contagem ao longo das faturas.

## Módulos

- `:feature:installments:api`
- `:feature:installments:impl`

> Sem `:ui`: a UI de parcelamento é consumida pelos modais de transação/cartão e por sua tela própria em `:impl`.

## Contratos públicos (`:api`)

- **Modelos:** `Installment` (id, count, totalAmount).
- **Repositórios:** `IInstallmentRepository` (CRUD + observe + `createInstallment`).
- **Use cases:** `IAddInstallmentUseCase` (`TransactionForm + installments: Int → Either<Throwable, List<Transaction>>`).
- **Eventos analytics:** `CreateInstallments`, `DeleteInstallments`.

## Implementação (`:impl`)

- **Tela:** `InstallmentsScreen` + `InstallmentsViewModel` (UiState/Action/Filter).
- **Modais:** `AddInstallmentModal` + `AddInstallmentViewModel`, `DeleteInstallmentModal`.
- **Repositório:** `InstallmentRepository`.
- **Use cases:** `AddInstallmentUseCase`.
- **Helpers:** `InstallmentUiMapper`.

## Dependências

- `:api`: `:feature:transactions:api`, `:core:utils`.
- `:impl`: `:api`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:accounts:api`, `:feature:accounts:ui`, `:feature:creditCards:api`, `:feature:creditCards:ui`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
