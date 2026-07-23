> Ordem deliberada: os refactors de arquitetura/design (D7–D11) vêm **antes** da UI de desarquivar, para que a nova ação nasça sobre a lógica já centralizada, não duplicando-a.

## 1. Domínio — retirabilidade e erro de retirada (D7, D11)

- [x] 1.1 Criar `RetireError` compartilhado em `core/model` (razões: `HAS_TRANSACTIONS`, `HAS_BUDGET`, `HAS_RECURRING`) com `val message` (inglês, log) e `toUiText()` (i18n via `UiText.Res`), no padrão de erro do projeto.
- [x] 1.2 Criar `CategoryRetirability` (`Deletable` | `MustArchive(reason: RetireError)`) e `ResolveCategoryRetirabilityUseCase` (injeta entry/budget/recurring repos) que resolve os três guardas num único lugar.
- [x] 1.3 `DeleteCategoryUseCase`: consumir `ResolveCategoryRetirabilityUseCase` e mapear `MustArchive.reason` → erro; parar de retornar `AccountException`/`AccountError`.
- [x] 1.4 Extrair `fun Throwable.toRetireUiMessage(): UiText` única (em `core/ui` ou `core/model`), substituindo a `toUiMessage()` duplicada em `DeleteCategoryViewModel` e `ArchiveCategoryViewModel`.
- [x] 1.5 Registrar `ResolveCategoryRetirabilityUseCase` no `categoriesModule` (Koin).

## 2. Camada de dados — desarquivar + robustez (D12)

- [x] 2.1 `CategoryDao`: `@Query("UPDATE categories SET isArchived = 0 WHERE id = :id") suspend fun unarchive(id: Long)`, ao lado de `archive`.
- [x] 2.2 `CategoryDao`: `@Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name COLLATE NOCASE AND id != :ignoreId)") suspend fun existsByName(name: String, ignoreId: Long): Boolean` (inclui arquivadas).
- [x] 2.3 `ICategoryRepository`: adicionar `unarchive(id)`, `existsByName(name, ignoreId)` e `insertAll(categories: List<Category>)` (KDoc simétrico a `archive`/`insert`).
- [x] 2.4 `CategoryRepository`: `unarchive` → `dao.unarchive`; `existsByName` → `dao.existsByName`; `insertAll` numa única `immediateTransaction` que emite as dimensões e insere as fachadas em bloco.

## 3. Domínio — UnarchiveCategoryUseCase + validação/criação corrigidas

- [x] 3.1 Criar `UnarchiveCategoryUseCase` espelhando `ArchiveCategoryUseCase` (`Either<Throwable, Unit>` via `catch`, chamando `unarchive`), com KDoc de "reversível e inócuo".
- [x] 3.2 `CreateDefaultCategoriesUseCase`: montar `List<Category>` e chamar `insertAll` uma vez (atômico), no lugar do laço de `insert`.
- [x] 3.3 `ValidateCategoryNameUseCase`: aplicar `trim` uma vez no topo (usado tanto por `isEmpty` quanto pela duplicidade); trocar a varredura O(n) por `repository.existsByName(trimmed, ignoreId)`. Remover o `// TODO`.
- [x] 3.4 Registrar `UnarchiveCategoryUseCase` no `categoriesModule`.

## 4. core/ui — componente de ação compartilhado (D9)

- [x] 4.1 Extrair `OutlinedActionButton(label, icon, contentColor, onClick, modifier, fontWeight = Medium)` em `core/ui`, com `RoundedCornerShape(12.dp)` + `BorderStroke(1.dp, contentColor)` + ícone 18.dp + `Text` em `MaterialTheme.typography.labelLarge`.
- [x] 4.2 Refatorar `ViewCategoryModal.DetailActions` (retirar/editar) e `AccountsScreen.AccountActions` (retirar/editar/transferir) para consumir `OutlinedActionButton`.
- [x] 4.3 `CategoryCard`: adicionar indicação **textual/iconográfica** de arquivada (ex.: `Text` "Arquivada" em `labelSmall`/`onSurfaceVariant` ou `Icons.Default.Archive`), além da cor esmaecida já existente — cor não pode ser o único diferenciador.

## 5. UI — refactor do modal + botão Desarquivar (D8, D1)

- [x] 5.1 `ViewCategoryModal`: resolver `koinViewModel` + coletar `uiState` **uma vez**, passando `uiState`/`onAction` para `DetailContent`/`DetailActions` como parâmetros (fim da dupla-resolução).
- [x] 5.2 Trocar `collectAsState()` por `collectAsStateWithLifecycle()`.
- [x] 5.3 `ViewCategoryViewModel`: consumir `ResolveCategoryRetirabilityUseCase` para `retireAction` (remover os 3 reads inline e as 3 injeções de repo dedicadas a `mustPreserve`).
- [x] 5.4 `ViewCategoryAction`: adicionar `data object Unarchive`.
- [x] 5.5 `ViewCategoryViewModel`: injetar `UnarchiveCategoryUseCase`; `onAction(Unarchive)` chama o use case com `onLeft { crashlytics.recordException(it) }`. Atualizar a construção do VM no Koin.
- [x] 5.6 `ViewCategoryModal.DetailActions`: no ramo `else` (categoria arquivada), renderizar `OutlinedActionButton` **Desarquivar** (ícone `Icons.Default.Unarchive`).
- [x] 5.7 Strings: `view_category_unarchive` ("Desarquivar").
- [x] 5.8 Limpezas locais: remover imports mortos `Expense`/`Income` e `val formatter` não usado em `DetailContent`; remover defaults de `ViewCategoryUiState.Content`; extrair `typeLabel` das expressões condicionais longas; typography (`bodyLarge`/`titleMedium`/`labelLarge`) no lugar de `fontSize`/`FontWeight` soltos nos modais (D6).

## 6. Redesenho da tela — estado

- [x] 6.1 Criar `CategoryFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` no pacote da tela.
- [x] 6.2 `CategoriesUiState`: trocar `selectedType` por `filter`; `Content` carrega as seções resolvidas (cada seção com cabeçalho opcional + categorias); `Empty` guarda o `filter` para o `initialType` do FAB.
- [x] 6.3 `CategoriesAction`: substituir `SelectType(type)` por `SelectFilter(filter)`.
- [x] 6.4 `CategoriesViewModel`: observar `observeAllCategoriesIncludingClosed()`; combinar com `MutableStateFlow<CategoryFilter>` (default `ACTIVE`); derivar as seções (ACTIVE = Despesas+Receitas de ativas, seccionado; EXPENSE/INCOME = ativas do tipo; ARCHIVED = arquivadas). Omitir seção vazia. Comentar que o predicado de "ativa" (`!isArchived`) espelha `OPEN_CATEGORIES` do DAO (B1).

## 7. Redesenho da tela — UI (D10, D5, D6)

- [x] 7.1 `CategoriesScreen`: remover `PrimaryTabRow` + `HorizontalPager` + sincronização de pager.
- [x] 7.2 Topbar transparente (`containerColor = colorScheme.background`, padrão `AccountsScreen`).
- [x] 7.3 Seletor de `FilterChip` (Ativas · Despesas · Receitas · Arquivadas) em `Row`/`LazyRow` → `SelectFilter`. Materializar as opções de `CategoryFilter.entries` (não recriar lista sem `remember`).
- [x] 7.4 Renderizar seções numa `LazyColumn` única (cabeçalho quando houver + `CategoryCard`s); card abre `ViewCategoryModal`.
- [x] 7.5 FAB: `initialType` resolvido do filtro (EXPENSE→despesa, INCOME→receita, ACTIVE/ARCHIVED→despesa).
- [x] 7.6 Dois empty-states distintos (D10): `EmptyDatabaseState` grande (CTA) só com banco vazio; `EmptyFilterState` compacto (texto/ícone, sem botão) para filtro vazio.
- [x] 7.7 Strings: cabeçalhos de seção, rótulos de chip (`categories_filter_active`, `categories_filter_archived`; reusar `categories_expense`/`categories_income`), e vazio por filtro.

## 8. Testes

- [x] 8.1 `ResolveCategoryRetirabilityUseCase`: cada guarda dispara sua `MustArchive.reason`; sem dependentes → `Deletable`.
- [x] 8.2 `UnarchiveCategoryUseCase`: chama `unarchive(id)` e retorna `Right(Unit)`.
- [x] 8.3 `ViewCategoryViewModelTest`: ação `Unarchive` invoca o use case; categoria arquivada oferece desarquivar (e não arquivar/apagar), não arquivada oferece retirar (e não desarquivar).
- [x] 8.4 `ValidateCategoryNameUseCase`: nome só com espaços é rejeitado como vazio; duplicidade case-insensitive ignora o próprio id; consulta via `existsByName`.
- [x] 8.5 `CategoriesViewModel`: ACTIVE não inclui arquivadas e vem seccionado; ARCHIVED lista só arquivadas; Empty grande só sem categorias.
- [x] 8.6 `CreateDefaultCategoriesUseCase`: usa `insertAll` (uma transação); ajustar/estender os fakes existentes.

## 9. Validação

- [x] 9.1 `openspec validate unarchive-and-redesign-categories --strict`.
- [x] 9.2 `./gradlew :app:shared:testDebugUnitTest` (categorias) verde.
- [ ] 9.3 Conferir na tela: arquivar → aparece em Arquivadas (com indicação) → abrir → Desarquivar → reaparece em Ativas e nos seletores de lançamento.
