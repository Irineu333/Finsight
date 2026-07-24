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
- Alinhar a tela ao design do app: topbar transparente + seletor de filtro, no lugar de tabs/pager.

**Non-Goals:**
- Desarquivar/reabrir **conta ou cartão** — esses fecham via conta contábil e estão fora de escopo.
- Qualquer migração de banco. O flag `categories.isArchived` já existe.
- Mudar o modelo de dados de categoria (tipo, dimensão, ícone permanecem como estão).
- **Dívida transversal do design system**, deixada para tarefas próprias (ambos os agentes concordaram em não resolver aqui): a dupla-resolução de `koinViewModel` estrutural ao `AdaptiveModal` (afeta 5 features — só corrigimos o sintoma local em Categorias via D8), o token `Info` sem role MD3, o `TypeToggle` que reimplementa um segmented control, a ausência de `@Preview`, e o N+1 de `CalculateCategorySpendingUseCaseImpl` (afeta dashboard/report, não a tela nova).

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

O seletor mistura dois eixos (tipo e status); "Ativas" (em vez de "Todas") deixa isso honesto — o rótulo "Todas" mentiria, pois arquivadas ficam de fora das três primeiras opções.
- *Alternativa considerada:* filtro de tipo + toggle "mostrar arquivadas" (dois eixos separados). Rejeitada: o pedido é um seletor único com "Arquivadas" como opção; o híbrido cobre isso e mantém um seletor único.

### D4 — UiState carrega a lista já resolvida; seções só em ACTIVE
`CategoriesUiState.Content(sections: List<Section>, filter: CategoryFilter, ...)`, onde uma `Section` tem um cabeçalho opcional e as categorias. Em `ACTIVE`, duas seções com cabeçalho; nos demais filtros, uma seção sem cabeçalho. A tela renderiza uniformemente uma `LazyColumn` de seções — sem `HorizontalPager`. Uma seção sem itens é omitida (ex.: só há despesas → "Ativas" mostra só a seção Despesas).

### D5 — FAB e empty-state
- `initialType` do `CategoryFormModal`: `EXPENSE` para o filtro `EXPENSE`, `INCOME` para `INCOME`, e `EXPENSE` (default) para `ACTIVE`/`ARCHIVED`.
- Empty grande (CTA "Usar padrão / Criar manualmente") **apenas** quando não há categoria alguma no banco. Filtro vazio (ex.: nenhuma arquivada) mostra um texto discreto por filtro, não o CTA.

### D6 — Topbar e seletor
- Topbar: `TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background, …)`, idêntico a `AccountsScreen`.
- Seletor: `DropdownMenu` nas `actions` da top bar — o mesmo lugar e forma em que o `AccountsScreen` mantém o controle de mês. O gatilho é um `TextButton` com o rótulo do filtro atual + chevron; o menu lista `CategoryFilter.entries` com um check no selecionado. Preferido a uma linha de `FilterChip`: encosta o seletor à direita do título em vez de gastar uma faixa horizontal só para quatro visões, e reaproveita o padrão que a tela de contas já estabeleceu.
  - *Alternativa considerada:* `FilterChip` numa `Row`/`LazyRow`. Rejeitada em favor do dropdown por consistência com `AccountsScreen` e por não roubar altura vertical da lista.

---

> **Decisões D7–D12** vêm da análise de arquitetura e design de código (dois agentes) sobre a tela e o modal atuais. São melhorias que **reforçam** o desarquivar e evitam que a nova ação nasça duplicando lógica. Ficam propositalmente **antes** da implementação da UI de desarquivar no `tasks.md`.

### D7 — Retirabilidade de categoria tem um dono único no domínio
Hoje `ViewCategoryViewModel` reconstrói, em prosa, os três guardas de `DeleteCategoryUseCase` (`hasEntriesForDimension || hasBudgetForCategory || hasRecurringForCategory`) para decidir `mustPreserve`. A regra "o que impede apagar uma categoria" fica com **dois donos**: um quarto dependente futuro faria a tela oferecer um DELETE que o domínio recusa. Extrair para o domínio:
```kotlin
sealed interface CategoryRetirability {
    data object Deletable : CategoryRetirability
    data class MustArchive(val reason: RetireError) : CategoryRetirability
}
class ResolveCategoryRetirabilityUseCase(/* entry, budget, recurring repos */) {
    suspend operator fun invoke(category: Category): CategoryRetirability
}
```
`DeleteCategoryUseCase` consome e mapeia `reason → UiText`; `ViewCategoryViewModel` consome e faz `retireActionOf(retirability !is Deletable)`. Um lugar decide, dois consomem. Isso remove 3 injeções de repositório do ViewModel. Quando o botão Desarquivar (D1) somar um ramo à decisão de "qual ação retirar oferecer", a lógica já estará centralizada.
- *Alternativa:* deixar a duplicação e sincronizar na mão. Rejeitada — é exatamente a "regra reimplementada sem dono" que o CLAUDE.md proíbe.

### D8 — O modal resolve o ViewModel e coleta o estado **uma vez**
`ViewCategoryModal` resolve `koinViewModel` + `collectAsState()` **duas vezes** (`DetailContent` e `DetailActions`), com dois assinantes do mesmo `StateFlow` e dependência frágil de os dois slots caírem no mesmo `ViewModelStoreOwner`. Resolver o VM e coletar `uiState` uma vez, passando `uiState`/`onAction` como parâmetros — como `CategoriesContent` já faz. Além disso, trocar `collectAsState()` por `collectAsStateWithLifecycle()` (o resto do feature e `AccountsScreen` já usam; agrava aqui porque `uiState` reage a `observeLedgerChanges()`). Pré-requisito limpo da task do botão Desarquivar.

### D9 — `OutlinedActionButton` compartilhado em `core/ui`
O bloco de botão de ação (`RoundedCornerShape(12.dp)` + `BorderStroke(1.dp, color)` + ícone 18.dp + `Text`) é reescrito em `ViewCategoryModal` (retirar/editar) e em `AccountsScreen.AccountActions` (retirar/editar/transferir). Extrair `OutlinedActionButton(label, icon, contentColor, onClick, modifier)` em `core/ui` **antes** de adicionar o Desarquivar, para que ele nasça reusando o componente, não como a quarta cópia. Também usa `MaterialTheme.typography.labelLarge` em vez de `fontSize`/`FontWeight` soltos.

### D10 — Dois empty-states distintos (refina D5)
"Banco sem categoria alguma" → CTA grande (`EmptyDatabaseState`, com "Usar padrão / Criar manualmente"). "Filtro atual sem itens, banco não vazio" (ex.: nenhuma arquivada) → `EmptyFilterState` compacto (texto + ícone monocromático, **sem** botão). Não reaproveitar o CTA para o segundo caso — empurraria "Usar categorias padrão" para quem só filtrou Arquivadas.

### D11 — Retirada de categoria fala a própria língua de erro
`DeleteCategoryUseCase` reporta `AccountException`/`AccountError` (categoria falando a língua de conta) e `Throwable.toUiMessage()` está duplicado byte a byte entre `DeleteCategoryViewModel` e `ArchiveCategoryViewModel`. Introduzir um `RetireError` compartilhado (`core/model`) com `toUiText()` e uma única `Throwable.toRetireUiMessage()` (`core/ui`/`core/model`), consumidos pelos dois ViewModels. `MustArchive.reason` de D7 mapeia para esse erro comum. Reduz o par archive/delete a diferir só no use case chamado.

### D12 — Robustez de dados (independente do redesenho, mas no mesmo módulo)
- **`CreateDefaultCategoriesUseCase` atômico:** hoje insere 14 categorias em 14 transações (`insert` em laço, cada uma abrindo sua `immediateTransaction`); falha no meio deixa defaults parciais — e é o CTA do empty-state que dispara. Adicionar `ICategoryRepository.insertAll(List<Category>)` numa única transação (emite as dimensões + insere as fachadas em bloco); uma só invalidação de Flow.
- **`ValidateCategoryNameUseCase` sem varredura O(n) por tecla:** hoje carrega `getAllCategoriesIncludingClosed()` e faz `.any` a cada tecla (tem `// TODO`), e valida "vazio" sem `trim` mas compara duplicidade com `trim` — inconsistência de fronteira. Trocar por query `SELECT EXISTS(... name = :name COLLATE NOCASE AND id != :ignoreId)` (incluindo arquivadas) e aplicar `trim` uma vez no topo, usado tanto pelo `isEmpty` quanto pela checagem.

## Risks / Trade-offs

- **Arquivadas visíveis reintroduzindo a categoria em algum seletor por engano** → os seletores de lançamento e `Budget.categories` continuam consumindo as leituras **ativas** (`observeAllCategories`/`observeCategoriesByType`), inalteradas; só a tela de categorias passa a `...IncludingClosed`. A visibilidade é local à tela.
- **Regressão visual/estado ao remover o pager** → a lógica de sincronização pager↔tab desaparece por completo; o estado do filtro é um único `MutableStateFlow<CategoryFilter>`, mais simples de raciocinar do que a dupla pager/selectedType atual.
- **Opção "Ativas" x opções de tipo podem parecer redundantes** → são visões (Ativas = seccionada; Despesas/Receitas = recorte simples), coerentes como conjunto; o rótulo "Ativas" evita a leitura de que "Todas" incluiria arquivadas.

## Migration Plan

Sem migração de dados. Mudança puramente de código; `categories.isArchived` já existe e o desarquivar é um `UPDATE` sobre ele. Rollback é reverter o código — nenhum dado fica em estado novo (uma categoria desarquivada é indistinguível de uma que nunca foi arquivada).

## Open Questions

- Nenhuma pendente. Vocabulário do seletor travado em **Ativas · Despesas · Receitas · Arquivadas**.
