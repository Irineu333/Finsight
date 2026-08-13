## Why

Uma transação pode ser criada sem categoria (`TransactionForm.category: Category?`), mas nenhum
widget de gastos por categoria admite isso: tanto o dashboard quanto o relatório resolvem cada
dimensão para uma categoria e descartam silenciosamente o que não resolve
(`CalculateReportCategorySpendingUseCase.kt:91`). O usuário lê um detalhamento cujas fatias somam
100% de um todo que não é o todo — e a parcela que falta é justamente a que ele poderia agir sobre.

O razão já sabe responder: `openspec/specs/ledger-reporting/spec.md` exige que "o total das entries
**sem dimensão** na conta nominal SHALL ser o total 'sem categoria'", e
`IEntryRepository.totalsByDimensionByCurrency` já devolve esse total sob a chave `null`. O número
existe, é lido, e é jogado fora antes de chegar à tela.

## What Changes

- Os widgets de **gastos por categoria** e de **receitas por categoria** — no dashboard e no
  relatório, incluindo a exportação HTML — passam a exibir uma linha "Sem categoria" quando há
  movimento sem classificação no período.
- **BREAKING (comportamental, não de API pública)**: a linha entra na escala comparativa, ou seja,
  **no denominador**. As porcentagens de todas as categorias mudam para quem tem gasto sem
  categoria; passam a somar 100% de verdade. Nenhum valor monetário exibido muda — apenas as fatias.
- A linha é fixada **por último**, independentemente da sua magnitude, e separada visualmente das
  categorias reais.
- A linha só existe quando o total é diferente de zero: um período inteiramente categorizado
  continua exibindo exatamente o que exibe hoje.
- O item do detalhamento deixa de ser uma `Category` e passa a ser um tipo-soma
  (`SpendingSubject`: `Categorized(Category)` ou `Uncategorized`). O não categorizado **não** vira
  uma categoria de sistema nem uma conta-balde: continua sendo a ausência de dimensão, como o razão
  exige.
- Nova leitura agregada no razão: totais por dimensão em um mês, por natureza de conta nominal,
  incluindo a chave nula — que substitui as N leituras por categoria que o dashboard faz hoje.

## Capabilities

### New Capabilities
- `uncategorized-spending-breakdown`: a regra de apresentação do total sem classificação num
  detalhamento por categoria — que ele existe como linha, que participa do denominador, onde é
  posicionado, quando é omitido, e que ele nunca é uma categoria.

### Modified Capabilities
- `ledger-reporting`: a leitura "total sem categoria" ganha a forma agregada com corte mensal —
  um mapa por dimensão (chave nula inclusa) para uma natureza de conta nominal num mês, no lugar de
  uma leitura por dimensão de cada vez.

## Impact

**Modelo (`core/model`)**
- `domain/model/CategorySpending.kt` — campo `category: Category` vira `subject: SpendingSubject`.
- `domain/model/SpendingSubject.kt` — novo.
- `domain/usecase/` — construtor único do detalhamento (ordenação, sinal, escala, descarte de
  zeros), hoje duplicado entre dashboard e relatório.

**Razão (`core/ledger`)**
- `database/dao/EntryDao.kt` — uma query nova.
- `domain/repository/IEntryRepository.kt` — um membro novo.

**Features**
- `categories/impl` — `CalculateCategorySpendingUseCaseImpl` / `CalculateCategoryIncomeUseCaseImpl`.
- `report/impl` — `CalculateReportCategorySpendingUseCase`, `ReportExportLayout`,
  `ReportExportStrings`.
- `dashboard/impl` — `DashboardComponentContent`, `DashboardPreviewFactory`.

**UI e recursos**
- `core/ui` — `CategorySpendingCard`.
- `core/resources` — chave `category_spending_uncategorized` em `values/` **e** `values-en/`.

**Sem migração de banco.** Nada aqui é persistido: todo número é derivado das entries.

**Testes afetados**: `CalculateCategorySpendingUseCaseImplTest`,
`ReportViewerViewModelCharacterizationTest` (caracterização — os percentuais mudam por desenho),
`ReportExportFootnoteTest`, `ReportExportAdjustmentToneTest` e os quatro testes de `dashboard/impl`
que fingem os use cases de categoria.
