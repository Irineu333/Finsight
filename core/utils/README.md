# `:core:utils`

Pure-Kotlin utilities shared across modules. No Compose, no Android, no business logic.

## Responsabilidade

Extensões e utilitários genéricos (Flow, datas, strings, coleções observáveis, debounce) reutilizáveis em qualquer camada.

## Conteúdo principal

- **Extensões:** `String.moneyToDouble()`, helpers de `LocalDate`/`YearMonth`.
- **Flow:** sobrecargas de `combine()` para 6+ flows com transformações tipadas.
- **Date patterns:** `dayMonth`, `dayMonthYear` para formatação de `LocalDate`.
- **Coleções reativas:** `ObservableMutableMap<K, V>` (Map exposto como `Flow`).
- **Debounce:** `DebounceManager` baseado em coroutines.
- **DI:** `utilsModule` (Koin) — registra `DebounceManager`.

## Dependências

Nenhuma intra-projeto. Apenas Kotlin stdlib, `kotlinx-coroutines`, `kotlinx-datetime` e Koin.

## Quem depende

- `:core:ui`, `:core:database` e todos os `:feature:X:impl` (transitivamente via outros core).
