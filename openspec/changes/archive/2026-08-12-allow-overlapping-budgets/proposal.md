## Why

Hoje uma categoria pertence a **no máximo um** orçamento: o formulário retira do seu seletor toda categoria já reivindicada por outro orçamento. A trava trata os orçamentos como uma **partição** da despesa — cada categoria com um dono — e com isso torna impossíveis dois usos legítimos: o **aninhamento** (um teto amplo de "Essenciais" contendo um sub-teto apertado de "Delivery") e o **recorte cruzado** (fixo × discricionário sobre as mesmas categorias).

A trava nunca desceu abaixo da camada de UI. O schema já é N:M de verdade, o progresso já é calculado orçamento a orçamento, e nenhuma tela soma orçamentos entre si — nada abaixo do formulário jamais dependeu da exclusividade. Removê-la não relaxa uma regra de domínio: apaga uma regra que só existia como filtro de dropdown.

## What Changes

- Uma categoria passa a poder participar de **quantos orçamentos o usuário quiser**. O seletor do formulário deixa de subtrair as categorias reivindicadas por outros orçamentos e passa a oferecer todas as categorias de despesa abertas.
- Um orçamento deixa de ser uma fatia exclusiva do gasto e passa a ser uma **lente independente** sobre ele: duas barras podem legitimamente medir a mesma despesa, porque respondem a perguntas diferentes.
- A continuidade de escolha já feita — uma categoria arquivada depois de adicionada permanece visível e removível, sem ser oferecida como opção nova — **permanece inalterada**. Ela era a outra metade da mesma função e é a única que sobrevive.
- A recusa de apagar uma categoria em uso por orçamento **continua existindo** e deixa de pressupor um único orçamento na sua redação, nos dois idiomas.
- Remoção de código morto descoberta no caminho: `AccountError.HAS_BUDGET` e a sua string `account_error_has_budget` são uma duplicata de `RetireError.HAS_BUDGET`, com mensagem sobre categoria dentro do enum de conta, e **nenhum caso de uso a emite**.

Nenhuma migração de banco, nenhum dado existente afetado: todo orçamento hoje válido continua válido, e o schema já aceita o novo estado sem alteração.

## Capabilities

### New Capabilities
- `budget-composition`: quais categorias um orçamento pode conter, o que o seu seletor oferece, o que uma barra de progresso significa quando categorias se sobrepõem, e como a recusa de apagar uma categoria em uso é redigida.

### Modified Capabilities
<!-- Nenhuma. Nenhum requisito existente codifica a exclusividade: `account-lifecycle`
     fala de categorias de orçamento apenas para exigir continuidade de escolha já feita
     (que esta mudança preserva), e `currency-consolidation` trata da denominação do
     limite, indiferente a quantos orçamentos leem a mesma categoria. -->

## Impact

**Código**
- `feature/budgets/impl` — `BudgetFormViewModel`: `offeredCategories` perde o parâmetro `otherBudgetCategoryIds` e o filtro; o cálculo de `budgetedCategoryIds` desaparece; o `combine` de `uiState` cai de quatro para três braços, porque `budgetRepository.observeAllBudgets()` só alimentava a trava (o repositório continua injetado, para o `submit`).
- `feature/budgets/impl` — `OfferedCategoriesTest`: o caso que exigia a subtração é invertido; os três casos de continuidade de arquivada permanecem.
- `core/model` — `AccountError`: remoção do valor `HAS_BUDGET` e do seu ramo em `toUiText`.
- `core/resources` — reescrita de `retire_error_has_budget` em `values` e `values-en`; remoção de `account_error_has_budget` nos dois.

**Não impactado (verificado)**
- `BudgetCategoryEntity` — chave primária composta `(budgetId, categoryId)`, sem `UNIQUE` em `categoryId`: o schema já admite N orçamentos por categoria.
- `BudgetDao` / `BudgetRepository` — a hidratação agrupa por `budgetId`, e `hasBudgetForCategory` é um `COUNT(*) > 0` que já contava N.
- `CalculateBudgetProgressUseCase` — o progresso é `budgets.map { … }`, cada barra somando as suas próprias dimensões; sobreposição não é dupla contagem, porque nada soma orçamentos entre si.
- `ResolveCategoryRetirabilityUseCase` — o guard é booleano e continua valendo tal como está.
- Dashboard e tela de orçamentos — nenhum total agregado entre orçamentos.
