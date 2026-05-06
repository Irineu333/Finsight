# `:feature:report`

## Responsabilidade

Gerar relatórios financeiros (intervalos de data, perspectivas income/expense, exportar/imprimir/compartilhar).

## Módulos

- `:feature:report:api`
- `:feature:report:impl`

## Contratos públicos (`:api`)

- **Modelos:** `ReportLayout`, `ReportPerspective`, `ReportDocument`, `PerspectiveTab`.

> Superfície pública pequena: o módulo é amplamente self-contained; o `:api` expõe apenas os tipos que `:app`/navegação precisam referenciar.

## Implementação (`:impl`)

- **Telas:** `ReportConfigScreen` + `ReportConfigViewModel`, `ReportViewerScreen` + `ReportViewerViewModel`.
- **Renderização:** `ReportDocumentRenderer` (interface) + `HtmlReportDocumentRenderer`.
- **Serviços:** `ReportPrintService`, `ReportShareService`.
- **Use cases:** `CalculateReportStatsUseCase`, `CalculateReportCategorySpendingUseCase`, `BuildReportViewerParamsUseCase`.
- **Navegação:** `ReportRoute`, `PerspectiveTabNavType`.
- **Eventos analytics:** `GenerateReport`, `ShareReport`, `PrintReport`.
- **Componentes internos:** `ReportContextCard`, `DateRangeCard`, `SectionsCard`.
- **Modelos internos:** `ReportViewerParams`, `ReportConfig`, `ReportExportLayout`.

## Dependências

- `:api`: `:core:utils`.
- `:impl`: `:api`, `:feature:accounts:api`, `:feature:accounts:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:creditCards:api`, `:feature:creditCards:ui`, `:feature:transactions:api`, `:feature:transactions:ui`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`, `kotlinx-serialization-json`.
