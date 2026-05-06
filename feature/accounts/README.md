# `:feature:accounts`

## Responsabilidade

Gerenciar contas (carteiras) onde transações são registradas: criação, edição, exclusão, ajuste de saldo, transferência entre contas e garantia de conta padrão.

## Módulos

- `:feature:accounts:api` — contratos públicos consumidos por outras features.
- `:feature:accounts:ui` — composables reutilizáveis para outras telas.
- `:feature:accounts:impl` — telas, ViewModels, modais e implementações de repositório/use cases.

## Contratos públicos (`:api`)

- **Modelos:** `Account` (id, name, iconKey, isDefault, createdAt).
- **Repositórios:** `IAccountRepository`.
- **Use cases:** `IEnsureDefaultAccountUseCase`.

## UI compartilhada (`:ui`)

- `AccountSelector`, `AccountCard`, `IAccountUiMapper`.

## Implementação (`:impl`)

- **Telas:** `AccountsScreen` + `AccountsViewModel`.
- **Modais:** `AccountFormModal`, `DeleteAccountModal`, `EditAccountBalanceModal`, `TransferBetweenAccountsModal`.
- **Repositório:** `AccountRepository` (Room).
- **Use cases:** `EnsureDefaultAccountUseCase`, ajuste de saldo e transferência (operam via `:feature:transactions:api`).

## Dependências

- `:api`: `:core:database` (entidade transitiva), `:core:utils`.
- `:ui`: `:api`, `:core:ui`.
- `:impl`: `:api`, `:ui`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
