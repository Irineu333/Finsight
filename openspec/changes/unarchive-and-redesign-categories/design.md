## Context

O ciclo de vida de categoria mora em `account-lifecycle`. Categoria é o único facade cujo arquivamento **não** passa pelo plano de contas: pela decisão D4, ela carrega o próprio `isArchived` (não tem conta contábil a fechar). Isso torna o arquivamento — e agora o desarquivamento — um simples flip de flag na fachada, sem envolvimento do razão.

Estado atual relevante:
- `CategoryDao` tem `archive(id)` (`SET isArchived = 1`) e já expõe `observeAllCategoriesIncludingClosed()`.
- `CategoriesViewModel` observa `observeAllCategories()` (só ativas) e a tela usa `PrimaryTabRow` + `HorizontalPager` com dois tipos.
- `ViewCategoryModal.DetailActions` já **oculta** o botão de retirar quando `isArchived` (linha ~175, `if (!content.category.isArchived)`) — a costura natural para o botão de desarquivar.
- `categoryDisplayColor(type, isArchived)` já esmaece a categoria arquivada; o `CategoryCard` renderiza o estado arquivado sem alteração.

## Goals / Non-Goals

**Goals:**
- Desarquivar categoria ponta a ponta (DAO → repo → use case → UI), simétrico ao arquivar.
- Tornar categorias arquivadas visíveis e alcançáveis, sem vazá-las para seletores/listagens ativas.
- Alinhar a tela ao design do app: topbar transparente + filtro por chips, no lugar de tabs/pager.

**Non-Goals:**
- Desarquivar/reabrir **conta ou cartão** — esses fecham via conta contábil e estão fora de escopo.
- Qualquer migração de banco. O flag `categories.isArchived` já existe.
- Mudar o modelo de dados de categoria (tipo, dimensão, ícone permanecem como estão).

## Decisions

### D1 — Desarquivar é ação direta, sem modal de confirmação
Arquivar tem `ArchiveCategoryModal` porque explica consequências (some de seletores/orçamentos). Desarquivar é reversível e inócuo, então é uma ação inline: novo `ViewCategoryAction.Unarchive` tratado no `ViewCategoryViewModel`, chamando `UnarchiveCategoryUseCase`. Como o modal observa `observeCategoryById`, ao desarquivar o `isArchived` vira falso e o próprio botão se troca de volta reativamente — sem `dismiss` necessário.
- *Alternativa considerada:* modal de confirmação simétrico ao arquivar. Rejeitada: confirmação para uma ação sem perda é atrito sem ganho, e a spec marca o desarquivar como inócuo.

### D2 — `UnarchiveCategoryUseCase` separado, espelhando `ArchiveCategoryUseCase`
Mesma forma: `Either<Throwable, Unit>` via `catch`, chamando `categoryRepository.unarchive(category.id)`. Mantém a simetria e o padrão "um use case por ação nomeada".
- *Alternativa considerada:* um único `SetCategoryArchivedUseCase(archived: Boolean)`. Rejeitada: a spec exige ações/use cases distintos e nomeados; um booleano genérico reintroduz o "use case que faz coisa diferente do seu nome".

### D3 — Filtro modelado como enum de 4 visões, particionado no ViewModel
`CategoryFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` substitui `selectedType`. O ViewModel passa a observar `observeAllCategoriesIncludingClosed()` e deriva o conteúdo:
- `ACTIVE` → seções [Despesas ativas] + [Receitas ativas]; arquivadas **excluídas**.
- `EXPENSE` / `INCOME` → lista simples de ativas daquele tipo.
- `ARCHIVED` → lista simples de arquivadas (ambos os tipos), cards esmaecidos.

O seletor mistura dois eixos (tipo e status); "Ativas" (em vez de "Todas") deixa isso honesto — o rótulo "Todas" mentiria, pois arquivadas ficam de fora dos três primeiros chips.
- *Alternativa considerada:* filtro de tipo + toggle "mostrar arquivadas" (dois eixos separados). Rejeitada: o pedido é um seletor único com "Arquivadas" como opção; o híbrido cobre isso e mantém uma linha de chips.

### D4 — UiState carrega a lista já resolvida; seções só em ACTIVE
`CategoriesUiState.Content(sections: List<Section>, filter: CategoryFilter, ...)`, onde uma `Section` tem um cabeçalho opcional e as categorias. Em `ACTIVE`, duas seções com cabeçalho; nos demais filtros, uma seção sem cabeçalho. A tela renderiza uniformemente uma `LazyColumn` de seções — sem `HorizontalPager`. Uma seção sem itens é omitida (ex.: só há despesas → "Ativas" mostra só a seção Despesas).

### D5 — FAB e empty-state
- `initialType` do `CategoryFormModal`: `EXPENSE` para o filtro `EXPENSE`, `INCOME` para `INCOME`, e `EXPENSE` (default) para `ACTIVE`/`ARCHIVED`.
- Empty grande (CTA "Usar padrão / Criar manualmente") **apenas** quando não há categoria alguma no banco. Filtro vazio (ex.: nenhuma arquivada) mostra um texto discreto por filtro, não o CTA.

### D6 — Topbar e seletor
- Topbar: `TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background, …)`, idêntico a `AccountsScreen`.
- Seletor: `FilterChip` numa `Row`/`LazyRow` — o app ainda não tem `SegmentedButton` nem chips; `FilterChip` é o caminho mais leve para 4 itens.

## Risks / Trade-offs

- **Arquivadas visíveis reintroduzindo a categoria em algum seletor por engano** → os seletores de lançamento e `Budget.categories` continuam consumindo as leituras **ativas** (`observeAllCategories`/`observeCategoriesByType`), inalteradas; só a tela de categorias passa a `...IncludingClosed`. A visibilidade é local à tela.
- **Regressão visual/estado ao remover o pager** → a lógica de sincronização pager↔tab desaparece por completo; o estado do filtro é um único `MutableStateFlow<CategoryFilter>`, mais simples de raciocinar do que a dupla pager/selectedType atual.
- **Chip "Ativas" x chips de tipo podem parecer redundantes** → são visões (Ativas = seccionada; Despesas/Receitas = recorte simples), coerentes como conjunto; o rótulo "Ativas" evita a leitura de que "Todas" incluiria arquivadas.

## Migration Plan

Sem migração de dados. Mudança puramente de código; `categories.isArchived` já existe e o desarquivar é um `UPDATE` sobre ele. Rollback é reverter o código — nenhum dado fica em estado novo (uma categoria desarquivada é indistinguível de uma que nunca foi arquivada).

## Open Questions

- Nenhuma pendente. Vocabulário dos chips travado em **Ativas · Despesas · Receitas · Arquivadas**.
