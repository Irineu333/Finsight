# `:feature:categories`

## Responsabilidade

Gerenciar a taxonomia de categorias (income/expense) usada para classificar transações; expor cálculo de gasto por categoria.

## Módulos

- `:feature:categories:api`
- `:feature:categories:ui`
- `:feature:categories:impl`

## Contratos públicos (`:api`)

- **Modelos:** `Category` (id, name, **iconKey: String**, type, createdAt), `Category.Type` (`INCOME`, `EXPENSE`), `CategorySpending` (category, amount, percentage).
- **Repositórios:** `ICategoryRepository` (observe por tipo, CRUD).

> `iconKey` é uma `String`. A construção do `CategoryLazyIcon` ocorre apenas em `:ui`/`:impl`, mantendo o `:api` livre de Compose.

## UI compartilhada (`:ui`)

- `CategorySelector`, `MultiCategorySelector`, `CategorySpendingCard`, `CategoryIconBox`.

## Implementação (`:impl`)

- **Tela:** `CategoriesScreen` + `CategoriesViewModel`.
- **Modais:** `CategoryFormModal`, `ViewCategoryModal`, `DeleteCategoryModal`.
- **Repositório:** `CategoryRepository` (Room).

## Dependências

- `:api`: `:core:utils`.
- `:ui`: `:api`, `:core:ui`.
- `:impl`: `:api`, `:ui`, `:feature:transactions:api`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
