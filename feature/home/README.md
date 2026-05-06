# `:feature:home`

Casca (shell) que organiza a navegação por abas e a chrome (bottom bar, FAB) que hospeda Dashboard e Transactions.

## Módulos

- `:feature:home:api` — contratos mínimos para que outras features integrem com a chrome (rotas, dispatcher, estado).
- `:feature:home:impl` — implementação da `HomeScreen`, navegação interna e chrome.

> Listada como "feature terminal" no plano original; ainda assim mantém um `:api` enxuto para evitar dependência de outras features no `:impl`.

## Contratos públicos (`:api`)

- **Rotas:** `HomeRoute` (sealed) — entradas para cada aba.
- **Dispatcher de navegação** consumido pelos `:impl` de outras features.
- **Estado de chrome:** `HomeChrome` (controla visibilidade/aparência da chrome durante a navegação).

## Implementação (`:impl`)

- `HomeScreen` (NavHost interno + scaffold).
- Bottom navigation bar e FAB.
- `HomeChromeStateHolder`.

## Dependências

- `:api`: `:feature:transactions:api` (referencia tipos de filtro nas rotas).
- `:impl`: `:api`, `:core:ui`, `:core:utils`, `:core:analytics`, `:feature:dashboard:ui`, `:feature:transactions:api`, `:feature:transactions:ui`.
