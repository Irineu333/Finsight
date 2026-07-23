## Why

Hoje uma categoria arquivada é **invisível** e **irreversível** pela interface: `observeAllCategories()` a esconde e não há nenhum caminho para trazê-la de volta. Arquivar por engano (ou porque a categoria deixou de ser usada e voltou a ser) é uma via de mão única. Além disso, a tela de categorias é a última que ainda usa `PrimaryTabRow` + `HorizontalPager`, destoando da topbar transparente e do padrão de filtro do resto do app.

## What Changes

- **Desarquivar categoria.** Nova operação simétrica ao arquivar, ponta a ponta: `unarchive` no DAO/repositório, `UnarchiveCategoryUseCase`, e um botão **Desarquivar** na visualização da categoria (`ViewCategoryModal`), exibido exatamente onde hoje o botão de retirar é ocultado para categorias arquivadas. Ação direta, sem modal de confirmação — é reversível e inócua.
- **Categorias arquivadas passam a ser visíveis** na tela de categorias, sob um filtro dedicado — o que dá acesso à visualização de onde se desarquiva. Elas continuam **fora** dos seletores de lançamento e das listagens ativas.
- **Redesenho da tela de categorias:**
  - Topbar transparente (`containerColor = colorScheme.background`), igual a `AccountsScreen`.
  - Remoção de `PrimaryTabRow` + `HorizontalPager`.
  - Seletor de filtro por chips: **Ativas · Despesas · Receitas · Arquivadas**.
  - Em **Ativas**, a lista vem dividida em seções (Despesas / Receitas); os demais filtros são listas simples.
- A tela passa a observar `observeAllCategoriesIncludingClosed()` e particiona por status/tipo no ViewModel.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: o comportamento de ciclo de vida de categoria já mora em account-lifecycle. -->

### Modified Capabilities
- `account-lifecycle`: adiciona o desarquivamento de categoria como operação suportada, e reconcilia o requisito atual — categoria arquivada some das listagens ativas e dos seletores, mas passa a ser **acessível** por uma listagem de arquivadas para poder ser desarquivada.

## Impact

- **`core/database`** — `CategoryDao.unarchive(id)`.
- **`feature/categories/api`** — `ICategoryRepository.unarchive(id)`.
- **`feature/categories/impl`** — `CategoryRepository.unarchive`, novo `UnarchiveCategoryUseCase` (+ registro no Koin module), `ViewCategoryAction.Unarchive` + tratamento no `ViewCategoryViewModel`, botão em `ViewCategoryModal`. Reescrita de `CategoriesScreen`, `CategoriesUiState`, `CategoriesAction`, `CategoriesViewModel` para o modelo de filtro + seções.
- **`core/resources`** — novas strings: rótulo do desarquivar, rótulos dos chips (Ativas/Arquivadas), cabeçalhos de seção, vazios por filtro.
- Sem migração de banco: apenas um `UPDATE` no flag existente `categories.isArchived`.
