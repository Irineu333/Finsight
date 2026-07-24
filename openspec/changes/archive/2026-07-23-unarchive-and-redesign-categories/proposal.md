## Why

Hoje uma categoria arquivada é **invisível** e **irreversível** pela interface: `observeAllCategories()` a esconde e não há nenhum caminho para trazê-la de volta. Arquivar por engano (ou porque a categoria deixou de ser usada e voltou a ser) é uma via de mão única. Além disso, a tela de categorias é a última que ainda usa `PrimaryTabRow` + `HorizontalPager`, destoando da topbar transparente e do padrão de filtro do resto do app.

## What Changes

- **Desarquivar categoria.** Nova operação simétrica ao arquivar, ponta a ponta: `unarchive` no DAO/repositório, `UnarchiveCategoryUseCase`, e um botão **Desarquivar** na visualização da categoria (`ViewCategoryModal`), exibido exatamente onde hoje o botão de retirar é ocultado para categorias arquivadas. Ação direta, sem modal de confirmação — é reversível e inócua.
- **Categorias arquivadas passam a ser visíveis** na tela de categorias, sob um filtro dedicado — o que dá acesso à visualização de onde se desarquiva. Elas continuam **fora** dos seletores de lançamento e das listagens ativas.
- **Redesenho da tela de categorias:**
  - Topbar transparente (`containerColor = colorScheme.background`), igual a `AccountsScreen`.
  - Remoção de `PrimaryTabRow` + `HorizontalPager`.
  - Seletor de filtro (dropdown na top bar): **Ativas · Despesas · Receitas · Arquivadas**.
  - Em **Ativas**, a lista vem dividida em seções (Despesas / Receitas); os demais filtros são listas simples.
- A tela passa a observar `observeAllCategoriesIncludingClosed()` e particiona por status/tipo no ViewModel.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: o comportamento de ciclo de vida de categoria já mora em account-lifecycle. -->

### Modified Capabilities
- `account-lifecycle`: adiciona o desarquivamento de categoria como operação suportada, e reconcilia o requisito atual — categoria arquivada some das listagens ativas e dos seletores, mas passa a ser **acessível** por uma listagem de arquivadas para poder ser desarquivada.

## Impact

Escopo ampliado após análise de arquitetura + design de código (dois agentes) sobre a tela e o modal — melhorias que reforçam o desarquivar (ver `design.md` D7–D12).

- **`core/database`** — `CategoryDao`: `unarchive(id)`, `existsByName(name, ignoreId)`.
- **`core/model`** — `RetireError` compartilhado (razões de retirada + `toUiText()`); `CategoryRetirability`.
- **`core/ui`** — `OutlinedActionButton` compartilhado (reusado por Categorias e `AccountsScreen`); `Throwable.toRetireUiMessage()`; indicação de arquivada no `CategoryCard`.
- **`feature/categories/api`** — `ICategoryRepository`: `unarchive`, `existsByName`, `insertAll`.
- **`feature/categories/impl`** — `CategoryRepository` (impls acima), novos `UnarchiveCategoryUseCase` e `ResolveCategoryRetirabilityUseCase`, `DeleteCategoryUseCase`/`ValidateCategoryNameUseCase`/`CreateDefaultCategoriesUseCase` ajustados, `ViewCategoryModal`/`ViewCategoryViewModel` (resolução única do VM + lifecycle + desarquivar + retirabilidade centralizada), reescrita de `CategoriesScreen`/`CategoriesUiState`/`CategoriesAction`/`CategoriesViewModel` (filtro + seções + dois empty-states), registros no `categoriesModule`.
- **`feature/accounts/impl`** — `AccountsScreen.AccountActions` passa a consumir `OutlinedActionButton`.
- **`core/resources`** — novas strings: desarquivar, opções do seletor (Ativas/Arquivadas), cabeçalhos de seção, vazios por filtro, mensagens de `RetireError`.
- Sem migração de banco: apenas um `UPDATE` no flag existente `categories.isArchived`.

**Fora de escopo** (dívida transversal, tarefas próprias): dupla-resolução estrutural do `AdaptiveModal` (5 features), token `Info` sem role MD3, `TypeToggle` como segmented control, `@Preview`, N+1 de `CalculateCategorySpendingUseCaseImpl`.
