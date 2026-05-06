# `:core:ui`

Design system Compose Multiplatform: tema, tipografia, componentes compartilhados, infraestrutura de modais e formatação.

## Responsabilidade

Tudo que é visual e reutilizável entre features. **Sem lógica de negócio**: apenas primitivas de UI, transformações de input, formatação e acessórios de Compose.

## Conteúdo principal

- **Tema:** `FinsightTheme()`, paleta (income/expense/adjustment, surfaces, status), `AppTypography`.
- **Modais:** `Modal` (base), `ModalBottomSheet` (com `ViewModelStore`), `ModalManager`, `ModalManagerHost()`, `LocalModalManager`. Modais utilitários: `DatePickerModal`, `DateRangePickerModal`, `IconPickerModal`.
- **Componentes:** `BalanceCard`, `MonthSelector`, `MonthPickerDropdownMenu`, `InstallmentCounter`, `IconPickerSelector`, `SharedTransitionProvider`, `FormattingLocalsHost`.
- **Ícones:** `AppIcon` (catálogo de Material icons usados pelo app).
- **Texto i18n:** `UiText` (`Raw`, `Res`, `ResWithArgs`), `stringUiText()`.
- **Input:** `DateInputTransformation`, `MoneyInputTransformation`, `rememberMoneyInputTransformation()`, `Validation` sealed class.
- **Formatação:** `CurrencyFormatter` (expect/actual), `DateFormats`; `LocalCurrencyFormatter`, `LocalDateFormats`.

## Dependências

- `:core:platform`
- `:core:utils`

## Quem depende

- Todos os `:feature:X:ui` e `:feature:X:impl`.
