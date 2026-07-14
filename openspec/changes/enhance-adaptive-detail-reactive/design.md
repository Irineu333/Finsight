## Context

O `adaptive-detail-pane` introduziu o `DetailPaneController` (slot único, painel largo ou bottom sheet estreito) para as superfícies de detalhe `view*`. Essas superfícies são **renderizadas por snapshot**:

- O modal (`AdaptiveModal`) recebe o **objeto de domínio carregado** no construtor (ex.: `ViewOperationModal(operation, perspective)`), inclusive via entry points de `feature/*/api` (`viewOperationModal(operationUi): AdaptiveModal`).
- O ViewModel usa esse objeto como `initialValue` e faz um `flow { emit(getById(id) ?: seed) }` **one-shot** (`ViewOperationViewModel.kt:22`) — nunca re-observa.

Consequência: o detalhe fica **stale** após uma edição. O `fix-stale-ui-after-delete` contornou isso acoplando `ModalManager.dismissAll()` ao `DetailPaneController` (`DesignSystemModule.kt`: `single { ModalManager(get()) }`), de modo que salvar/deletar **fecha** o detalhe. Aquele design registrou como Non-Goal explícito: "re-observar o detalhe in-place fica como melhoria futura fora de escopo".

Inventário atual dos 5 detalhes (maturidade desigual):

| Detalhe | VM | UiState | Fonte |
|---|---|---|---|
| viewTransaction (Operation) | ✅ | `data class` (só Content) | one-shot `getOperationById` |
| viewAdjustment | ✅ | `data class` | one-shot |
| viewCategory | ✅ | `data class` | one-shot |
| viewRecurring | ❌ | ❌ | objeto puro, sem VM |
| viewBudget | ❌ | ❌ | objeto puro, sem VM |

Os repositórios expõem `observeAll*()` e `get*ById(id)` (suspend), mas **não** `observe*ById(id): Flow<T?>`.

## Goals / Non-Goals

**Goals:**
- Detalhes `view*` **reativos por id**: editar re-renderiza o detalhe in-place, sem fechá-lo.
- Cada `view*` ViewModel emite `sealed UiState` com `Loading | Error | Content`, com `Loading` inicial.
- Exclusão da entidade com detalhe aberto **auto-dispensa** o detalhe pela observação do `null`, de forma dirigida.
- Padronizar os 5 detalhes (incluir VM em `viewRecurring` e `viewBudget`).
- Reverter o acoplamento `dismissAll() → DetailPaneController`, restaurando `dismissAll()` como teardown **apenas** da pilha transitória.

**Non-Goals:**
- Introduzir **classe base** para os ViewModels de detalhe — o padrão é repetido por convenção em cada VM (decisão do dono).
- Manter o **seed**/objeto carregado: os construtores passam a receber **apenas id (+ config não-recuperável)**.
- Tornar `DashboardComponentOptionsModal` reativo — é config de UI de edição do dashboard, não entidade de domínio; fica fora de escopo.
- Alterar o `AdaptiveModal`/`DetailPaneController`/apresentação painel-vs-sheet, ou os ~21 `DeleteXxxViewModel`.

## Decisions

### Decisão 1 — Construtor por id, sem seed

Cada `view*Modal` e seu ViewModel recebem o **id** (primitivo) e, quando aplicável, a **configuração de apresentação não-recuperável** (ex.: `OperationPerspective`, que seleciona qual transaction da operation exibir e não deriva do id). O objeto de domínio deixa de trafegar pela API.

```kotlin
// api (BREAKING)
fun viewOperationModal(operationId: Long, perspective: OperationPerspective? = null): AdaptiveModal
```

- **Por quê:** o id é a fonte única da verdade reativa; passar o objeto reintroduz snapshot stale. Vazar o id para a `api` **limpa** a assinatura (deixa de acoplar a `OperationUi` inteira).
- **Alternativa rejeitada (seed-and-observe):** aceitar `id + seed?` para evitar o flash de `Loading`. Rejeitada pelo dono — preferência por passar apenas o necessário; o `Loading` inicial é aceito.

### Decisão 2 — `sealed UiState` por VM, sem classe base

Cada VM define seu próprio `sealed interface View*UiState { Loading; Error; Content(...) }` carregando o payload da feature, e implementa a observação/roteamento **inline**.

```kotlin
val uiState = repository.observeOperationById(operationId)
    .map { op ->
        when {
            op != null -> { loadedOnce = true; Content(op, perspective) }
            loadedOnce -> { _events.send(Dismiss); Loading } // sumiu → auto-dismiss
            else       -> Error                               // primeira emissão null
        }
    }
    .stateIn(viewModelScope, WhileSubscribed(5000), Loading)
```

- **Por quê sem base:** CLAUDE.md prioriza simplicidade sobre abstração; a lógica são ~3 linhas visíveis por VM. Uma base genérica esconderia decisão de UI atrás de herança/tipos genéricos.
- **Trade-off:** o padrão (Loading inicial, roteamento do `null`, evento de dismiss) é **duplicado** nos 5 VMs. Aceito como duplicação de mecânica visível, não de lógica de negócio.

### Decisão 3 — Roteamento do `null`: `Error` vs. auto-dismiss

`observe*ById(id)` emite `null` em dois momentos semanticamente distintos, desambiguados por um flag local `loadedOnce`:

```
primeira emissão null  → Error        (id inválido / não encontrado)
null após Content      → auto-dismiss (entidade deletada com detalhe aberto)
```

O auto-dismiss é feito por **evento** (`Channel`) que o `AdaptiveModal` coleta em `LaunchedEffect` e traduz para `LocalDetailPaneController.current.dismiss()` — mesma mecânica do evento `OpenRecurring` já existente em `ViewOperationModal.kt:78`.

- **Por quê no VM (e não na base):** é onde o flag `loadedOnce` vive. Segue o padrão `events`/`Channel.receiveAsFlow()` já usado no projeto.

### Decisão 4 — Reverter o acoplamento `dismissAll() → DetailPaneController`

Com o auto-dismiss reativo cobrindo a exclusão, `ModalManager` deixa de compor o `DetailPaneController`; `dismissAll()` volta a limpar **apenas** `modalState`. DI volta a `single { ModalManager() }`.

- **Por quê:** o acoplamento era a causa de o detalhe fechar ao **salvar** (efeito colateral que o `fix-stale-ui-after-delete` aceitou). O delete agora é tratado de forma **dirigida** pela observação do `null`; salvar apenas re-renderiza.
- **Pré-condição:** exige que os **5** `view*` estejam reativos antes da reversão — senão um detalhe não-migrado volta a ficar órfão ao deletar. Por isso a migração é coordenada dentro deste change (ver Migration Plan).

### Decisão 5 — Fonte do `observe*ById`

Preferir uma query Room dedicada (`@Query ... WHERE id = :id` retornando `Flow<Entity?>`) quando o DAO/mapeamento permitir montar o modelo de domínio diretamente; quando o modelo exigir composição já feita por `observeAll*()` (ex.: operação com transactions/invoice/installment agregados), derivar via `observeAll*().map { it.firstOrNull { it.id == id } }`.

- **Trade-off:** derivar de `observeAll` re-emite a lista inteira a cada mudança; aceitável para o volume do app e evita duplicar a lógica de composição do domínio. Decidir por entidade na implementação.

## Risks / Trade-offs

- **Flash de `Loading` ao abrir a partir de listas** (o objeto já está em memória, mas re-carregamos por id) → Mitigação: `observe*ById` é query local Room (rápida); UI de `Loading` enxuta (skeleton/spinner discreto); no painel largo o estado aparece no espaço já reservado.
- **Reverter `dismissAll` antes de todos os `view*` reativos deixa órfãos** → Mitigação: migrar os 5 detalhes primeiro; a reversão do acoplamento é o **último** passo do change.
- **`null` mal roteado (Error onde deveria dismiss, ou limbo em Loading)** → Mitigação: o flag `loadedOnce` e os cenários de spec cobrem os dois caminhos; é a mesma família do limbo corrigido em `SupportIssueViewModel`.
- **Duplicação do padrão nos 5 VMs** (sem base) → Mitigação: manter os VMs consistentes entre si facilita revisão; a mecânica é curta e explícita.

## Migration Plan

1. Adicionar `observe*ById(id): Flow<T?>` nos repositórios/DAOs das 5 entidades.
2. Migrar cada `view*` (VM + UiState `Loading/Error/Content` + Modal com UI de Loading/Error + construtor por id), incluindo criar VM em `viewRecurring` e `viewBudget`.
3. Atualizar as assinaturas dos entry points `view*Modal(...)` em `feature/*/api` para receberem id (+ config) e todos os call-sites cross-feature.
4. **Somente após os 5 migrados:** reverter o acoplamento `dismissAll() → DetailPaneController` em `core/designsystem` e ajustar a DI.

Rollback: cada passo 1–3 é reversível por feature; o passo 4 só deve ser revertido junto com o retorno de ao menos um `view*` ao modo snapshot.
