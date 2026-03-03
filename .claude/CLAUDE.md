# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

## iOS
The iOS project uses **XcodeGen** (`iosApp/project.yml`).

## Commands
```bash
./gradlew allTests              # All tests
./gradlew check                 # Verification
```

## Architecture
Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels → UiState + Actions

**Layers:**
- `/domain/`: Repositories (interfaces), UseCases, Error types (business rules, framework-independent)
- `/database/`: Room entities, DAOs, Mappers, Repository implementations (data sources)
- `/ui/`: Screens (composables, ViewModels, UiState), Modals, Components (presentation)

**Dependency Rule:** Domain ← Database, Domain ← UI (domain has no dependencies)

**DI (Koin):** `viewModel {}` screens, `factory {}` use cases, `single {}` repositories

**Navigation:** Type-safe sealed routes (App-level + Home-level nested)

**Modals:** `ModalManager` via `LocalModalManager`, extend `ModalBottomSheet`

**Error Handling:** Arrow library (Either/flatMap/catch)

**Error Types** (`/domain/error/`): `enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via string resources, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

## Code Style
- Documentation is the code (avoid comments, write clear code).
- Follow best programming practices (Return First Pattern, SOLID, DRY).
- High cohesion and low coupling.
- Don't make the code worse.
