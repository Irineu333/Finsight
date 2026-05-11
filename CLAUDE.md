# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

## Commands
```bash
./gradlew allTests                                          # All tests
./gradlew check                                            # Verification
./gradlew :app:testDebugUnitTest --tests "*.XxxTest" # Single test class
./gradlew :app:testDebugUnitTest                    # Unit tests only
```

## Features
- **Dashboard**: balance overview, credit card summaries, account list
- **Transactions**: income/expense list with filters (account, category, month)
- **Accounts**: account management, balance adjustments, transfers between accounts
- **Credit Cards**: card management, invoice lifecycle (open/close/pay/reopen), invoice balance adjustments
- **Installments**: installment tracking across invoices
- **Recurring**: recurring transactions (confirm/skip/stop/reactivate)
- **Categories**: category management with icons, spending tracking
- **Budgets**: budget progress per category

**Module convention (api/impl):**
- Cada feature vive em `feature/<name>/` com módulos Gradle `:api` (modelos, interfaces de repositório/use case), `:ui` (composables compartilháveis, opcional) e `:impl` (telas, ViewModels, modais, repositórios e use cases concretos). Features terminais (dashboard, support) podem omitir `:api`.
- Módulos `:core:*` (`utils`, `platform`, `ui`, `analytics`, `auth`, `database`) são transversais e não contêm lógica de negócio.
- **Regras de dependência:**
  - `:feature:X:impl` pode depender de `:feature:Y:api` mas **nunca** de `:feature:Y:impl`.
  - `:api` não depende de Compose nem de outros `:api` que voltariam a si (grafo é DAG).
  - Apenas `:app` agrega todos os `:impl` para wiring de DI/navegação.
- Lista autoritativa de módulos: `settings.gradle.kts`. Detalhes por módulo: `README.md` na raiz de cada feature/core (ver `## Modules` abaixo).

## Modules

**Core**
- `:core:utils` — extensões puras Kotlin, Flow combiners, datas, `DebounceManager` ([README](core/utils/README.md))
- `:core:platform` — detecção de plataforma (`Platform`, `currentPlatform`) ([README](core/platform/README.md))
- `:core:ui` — design system Compose: tema, modais, componentes, `UiText`, formatação ([README](core/ui/README.md))
- `:core:analytics` — interfaces `Analytics` e `Crashlytics` (impl Firebase) ([README](core/analytics/README.md))
- `:core:auth` — `AuthService` (impl Firebase Auth) ([README](core/auth/README.md))
- `:core:database` — Room: `AppDatabase`, entities, DAOs ([README](core/database/README.md))

**Features**
- `:feature:accounts` — contas/carteiras, transferência, ajuste de saldo ([README](feature/accounts/README.md))
- `:feature:categories` — taxonomia de categorias e cálculo de gasto por categoria ([README](feature/categories/README.md))
- `:feature:creditCards` — cartões e ciclo de faturas (open/close/pay/reopen) ([README](feature/creditCards/README.md))
- `:feature:transactions` — registro de transações, operações, cálculo de saldo ([README](feature/transactions/README.md))
- `:feature:installments` — parcelamento de transações ao longo de faturas ([README](feature/installments/README.md))
- `:feature:recurring` — transações recorrentes e ocorrências ([README](feature/recurring/README.md))
- `:feature:budgets` — orçamentos por categoria e progresso ([README](feature/budgets/README.md))
- `:feature:report` — geração de relatórios (export/print/share) ([README](feature/report/README.md))
- `:feature:dashboard` — tela inicial com cards e widgets ([README](feature/dashboard/README.md))
- `:feature:home` — shell de navegação por abas ([README](feature/home/README.md))
- `:feature:support` — tickets de suporte (Firestore) ([README](feature/support/README.md))

## Useful Paths

**Extensions (`/extension/`):** Useful extensions for common types

**Utilities (`/util/`):** General-purpose utilities

## Conventions

**Architecture:** Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels -> UiState + Actions

**Dependency Rule:** Domain <- Database, Domain <- UI (domain has no dependencies)

**DI (Koin):** `viewModel {}` screens, `factory {}` use cases, `single {}` repositories

**Navigation:** Type-safe sealed routes (App-level + Home-level nested)

**Modals:** `ModalManager` via `LocalModalManager`, extend `ModalBottomSheet`

**Modal entries (`:feature:*:api`):** entries recebem `Long` ids (+ parâmetros transientes não-entidade como `OperationPerspective`, `Category.Type`, `LocalDate`) — nunca modelos de domínio. Imports de `feature/*/model/*` em `:api` ficam restritos a tipos de seleção. ViewModels resolvem a entidade pelo id no init. UiState de modal id-driven segue `sealed { Loading, Content[, Error] }`: `Error` indica falha de hidratação (entidade deletada por race) e renderiza `ModalErrorContent` em vez de `dismiss()`. Forms em modo criação (id == null) emitem `Content` no primeiro frame — `Loading` só vale em edit-mode com fetch real.

**Error Handling:** Arrow library (Either/flatMap/catch)

> More details in the architecture skill.
> The iOS project uses **XcodeGen** (`iosApp/project.yml`).

## Strings & Internationalization

**`UiText`** (`/util/UiText`) — sealed class for UI-safe text:
- `UiText.asString()` — suspend, for non-Composable contexts
- `stringUiText(error: UiText): String` — `@Composable`, for UI display

**String resources:** `app/src/commonMain/composeResources/values/strings.xml`

> Always use `UiText.Res` for user-facing messages. `UiText.Raw` only for dynamic/runtime values with no translation.

## Error Types (`/domain/error/`)
`enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via `UiText.Res`, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

## Code Style
- Write clear code; comments are the exception, not a crutch.
- Prefer simplicity to abstractions that increase complexity (overengineering), prioritizing:
    1. Do not duplicate logic
    2. Do not increase complexity
    3. Do not duplicate code
- Apply DRY with judgment, not mechanically. 
- Reuse code in an explicit way with low coupling, without hiding business decisions.
- Extract code when there is a clear responsibility, high cohesion, and low coupling. 
- Every change must maintain or improve code clarity.