---
area: categories
severity: low
type: data
---

# O KDoc de arquivar categoria afirma que ela sai de `Budget.categories`; ela fica, por decisão

## Invariante

O KDoc descreve o estado atual do código.

Hoje é falso em `ArchiveCategoryUseCase`, cujo KDoc afirma que marcar a categoria como
arquivada "is what removes it from the pickers **and from `Budget.categories`**". A primeira
metade vale; a segunda é o **oposto** do que o código faz — e não por acaso: a hidratação foi
deliberadamente invertida por uma correção, e tem teste e spec por trás.

## Mecânica

`BudgetRepository.observeAllBudgets()` hidrata as categorias de um orçamento a partir de
`ICategoryRepository.observeAllCategoriesIncludingClosed()`, e o comentário ao lado da chamada
nomeia as três falhas que a lista aberta-somente causava: a categoria sumia do orçamento, o
gasto dela caía fora do progresso, e a edição seguinte — ressemeada dessa mesma lista — apagava
a linha de `budget_categories` de vez.

Três consumidores dependem de a categoria arquivada **continuar** em `budget.categories`:
`CalculateBudgetProgressUseCase` dobra `budget.categories.filter { it.type.isExpense }` para
somar o gasto do mês; `BudgetFormViewModel.offeredCategories()` / `withAlreadyChosen()` só
conseguem oferecer a arquivada **marcada**, para que possa ser desmarcada, porque ela chega ali
pela escolha já feita; e a spec `budget-composition` exige exatamente isso.

O KDoc era verdadeiro quando foi escrito, e o commit que mudou o comportamento no mesmo dia não
o atualizou; a reescrita seguinte do KDoc carregou a oração antiga adiante.

## Evidência

- `feature/categories/impl/.../usecase/ArchiveCategoryUseCase.kt` — o KDoc da classe, a oração
  "which is what removes it from the pickers and from `Budget.categories`"
- `feature/budgets/impl/.../repository/BudgetRepository.kt` — `observeAllBudgets()`:
  `categoryRepository.observeAllCategoriesIncludingClosed()`, e o comentário que explica por quê
- `feature/budgets/impl/src/commonTest/.../repository/BudgetClosedCategoryTest.kt` — o KDoc da
  classe: "A budgeted category that is later archived is **kept**, not filtered out"
- `feature/budgets/api/.../usecase/CalculateBudgetProgressUseCase.kt` — o `fold` sobre
  `budget.categories`
- `feature/budgets/impl/.../modal/budgetForm/BudgetFormViewModel.kt` — `offeredCategories()` e
  `withAlreadyChosen()`
- `openspec/specs/budget-composition/spec.md` — a categoria arquivada depois de adicionada
  continua aparecendo, marcada, para poder ser removida
- `core/database/.../dao/CategoryDao.kt` — `observeCategoriesByType` sobre `OPEN_CATEGORIES`: a
  metade verdadeira da frase, a das *pickers*

## Consequência

Quem for corrigir um bug de orçamento a partir do caso de uso de arquivamento lê que a categoria
já saiu do orçamento e conclui que a linha de `budget_categories` é lixo — precisamente a
leitura que a correção de `03149f7e3` existe para impedir. Não engana o usuário; engana o
próximo leitor, no ponto em que o app já cometeu esse erro uma vez.

## Sugestão

Trocar a oração por uma que descreva o que o arquivamento faz de fato — sai dos seletores,
permanece no orçamento e nas leituras de gasto —, apontando para `BudgetRepository` como o dono
dessa decisão. Não vinculante.
