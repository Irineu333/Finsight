## 1. Camada de dados — desarquivar

- [ ] 1.1 `CategoryDao`: adicionar `@Query("UPDATE categories SET isArchived = 0 WHERE id = :id") suspend fun unarchive(id: Long)`, ao lado de `archive`.
- [ ] 1.2 `ICategoryRepository` (api): adicionar `suspend fun unarchive(id: Long)` com KDoc simétrico ao de `archive`.
- [ ] 1.3 `CategoryRepository` (impl): implementar `override suspend fun unarchive(id: Long) = dao.unarchive(id)`.

## 2. Domínio — UnarchiveCategoryUseCase

- [ ] 2.1 Criar `UnarchiveCategoryUseCase` espelhando `ArchiveCategoryUseCase`: `suspend operator fun invoke(category: Category): Either<Throwable, Unit> = catch { categoryRepository.unarchive(category.id) }`, com KDoc explicando que é reversível e inócuo (não toca dimensão nem entries).
- [ ] 2.2 Registrar no `categoriesModule` (Koin): `factory { UnarchiveCategoryUseCase(categoryRepository = get()) }`.

## 3. UI — desarquivar na visualização da categoria

- [ ] 3.1 `ViewCategoryAction`: adicionar `data object Unarchive`.
- [ ] 3.2 `ViewCategoryViewModel`: injetar `UnarchiveCategoryUseCase`; em `onAction(Unarchive)`, chamar o use case em `viewModelScope`, com `onLeft { crashlytics.recordException(it) }`. Atualizar a construção do ViewModel no `categoriesModule`.
- [ ] 3.3 `ViewCategoryModal.DetailActions`: no ramo `else` do `if (!content.category.isArchived)`, renderizar o botão **Desarquivar** (par com o Editar). Reaproveitar o layout dos `OutlinedButton` existentes; ícone `Icons.Default.Unarchive`.
- [ ] 3.4 Strings: adicionar `view_category_unarchive` ("Desarquivar") em `core/resources/.../values/strings.xml`.

## 4. Redesenho da tela de categorias — estado

- [ ] 4.1 Criar `CategoryFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` no pacote da tela.
- [ ] 4.2 `CategoriesUiState`: trocar `selectedType` por `filter: CategoryFilter`; `Content` passa a carregar as seções resolvidas (lista de seções, cada uma com cabeçalho opcional + categorias). `Empty` guarda o `filter` para o `initialType` do FAB.
- [ ] 4.3 `CategoriesAction`: substituir `SelectType(type)` por `SelectFilter(filter)`; manter `CreateDefaultCategories`.
- [ ] 4.4 `CategoriesViewModel`: observar `observeAllCategoriesIncludingClosed()`; combinar com `MutableStateFlow<CategoryFilter>` (default `ACTIVE`); derivar as seções conforme D3/D4 (ACTIVE = seções Despesas+Receitas de ativas; EXPENSE/INCOME = ativas do tipo; ARCHIVED = arquivadas). Omitir seção vazia. Manter Empty grande só quando não há categoria alguma.

## 5. Redesenho da tela de categorias — UI

- [ ] 5.1 `CategoriesScreen`: remover `PrimaryTabRow` + `HorizontalPager` + sincronização de pager.
- [ ] 5.2 Topbar transparente: `TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background, …)` (padrão de `AccountsScreen`).
- [ ] 5.3 Seletor de `FilterChip` (Ativas · Despesas · Receitas · Arquivadas) numa `Row`/`LazyRow`, disparando `SelectFilter`.
- [ ] 5.4 Renderizar as seções numa `LazyColumn` única: cabeçalho de seção (quando houver) + `CategoryCard`s; card abre `ViewCategoryModal` via `detailController`.
- [ ] 5.5 FAB: `initialType` resolvido do filtro (EXPENSE→despesa, INCOME→receita, ACTIVE/ARCHIVED→despesa).
- [ ] 5.6 Vazio por filtro: texto discreto quando o filtro atual não tem itens (ex.: nenhuma arquivada), preservando o CTA grande só para banco sem categorias.
- [ ] 5.7 Strings: cabeçalhos de seção e rótulos de chip (`categories_filter_active`, `categories_filter_archived`; reaproveitar `categories_expense`/`categories_income`) e vazios por filtro.

## 6. Testes

- [ ] 6.1 Teste do `UnarchiveCategoryUseCase` (fake repository): desarquivar chama `unarchive(id)` e retorna `Right(Unit)`.
- [ ] 6.2 `ViewCategoryViewModelTest`: ação `Unarchive` invoca o use case; para categoria arquivada o estado oferece desarquivar (e não arquivar/apagar).
- [ ] 6.3 Teste do `CategoriesViewModel`: particionamento por filtro — ACTIVE não inclui arquivadas e vem seccionado; ARCHIVED lista só arquivadas; Empty grande só sem categorias.

## 7. Validação

- [ ] 7.1 `openspec validate unarchive-and-redesign-categories --strict`.
- [ ] 7.2 `./gradlew :app:shared:testDebugUnitTest` (categorias) verde.
- [ ] 7.3 Conferir manualmente na tela: arquivar → aparece em Arquivadas → abrir → Desarquivar → reaparece em Ativas e nos seletores de lançamento.
