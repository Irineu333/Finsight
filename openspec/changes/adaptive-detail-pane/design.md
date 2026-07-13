## Context

O único eixo de layout adaptativo hoje é a **navegação** (bottom bar → rail em ≥600dp, via `isWideWindow()` em `core/designsystem/.../ui/util/WindowSize.kt`). Os **modais não adaptam**: `ModalBottomSheet` (`core/designsystem/.../ui/component/ModalManager.kt:80`) sempre ancora embaixo e estica na largura toda. Em janelas muito largas os detalhes `view*` viram uma faixa larga e rasa — distorção visual.

Estado atual relevante:
- Todos os modais passam por um `ModalManager` singleton (`ModalManager.kt:24`), uma `mutableStateListOf<Modal>` (pilha) desenhada numa camada de **overlay** por cima de tudo, montada uma vez em `ModalManagerHost` (`app/shared/.../ui/App.kt:40`), **fora** do `ChromeHost`.
- `ModalBottomSheet` implementa `ViewModelStoreOwner` (cada modal escopa seu próprio `ViewModelStore`) e expõe um único método de conteúdo `ColumnScope.BottomSheetContent()`.
- A casca (`feature/shell/impl/.../home/ChromeHost.kt`) monta, no ramo largo, um `Row { rail; content(padding) }` (linha 160). A casca já resolve preocupações adaptativas de nível-shell via `ChromeController` + `LocalChromeController` (`feature/shell/api/.../Chrome.kt`), com features publicando intenção sem saber se vira rail ou bottom bar.
- 5 modais são de **detalhe/informação** (`view*`): `ViewOperationModal`, `ViewAdjustmentModal` (transactions), `ViewCategoryModal` (categories), `ViewBudgetModal` (budgets), `ViewRecurringModal` (recurring). Os demais ~30 são formulários/confirmações. Todos são obtidos por outras features via `<Name>Entry` (retornando o tipo base `Modal`).

## Goals / Non-Goals

**Goals:**
- Exibir os 5 detalhes `view*` num painel fixo à direita, **sempre reservado** em janelas largas, com empty-state e botão de fechar (X).
- Manter bottom sheet em janelas estreitas, sem regressão.
- Concentrar a decisão painel-vs-sheet na casca, mantendo as features **agnósticas de layout** (só publicam "aqui está um detalhe").
- Preservar o estado do detalhe ao cruzar o breakpoint (sheet ⇄ painel sem reabrir).
- Não tocar o `ModalManager` nem o fluxo dos modais de formulário/confirmação.

**Non-Goals:**
- Adaptar formulários e confirmações (continuam bottom sheet em qualquer largura).
- List-detail / master-detail com navegação por rota (o painel não é um destino de navegação).
- Histórico "voltar" dentro do painel (detalhe sobre detalhe **substitui**).
- Multiple back stacks ou preservação de pilha por seção (fora de escopo, já rejeitado no trabalho de navegação).

## Decisions

### Decisão 1 — Mecanismo separado (`DetailPaneController`), não reusar a pilha do `ModalManager`

Um detalhe é **bimodal por largura** (estreito=sheet, largo=painel) e o painel é um **slot único** com empty-state — não uma pilha. Modelar isso dentro da `mutableStateListOf<Modal>` do `ModalManager` exigiria filtrar "topo prefersPane" de uma pilha, dando ao `ModalManager` responsabilidade dupla e semântica de pilha onde a realidade é single-slot (fechar revelaria o detalhe anterior — indesejado num painel reservado).

Optamos por um **`DetailPaneController`** dedicado (irmão do `ChromeController` já existente), com um único `current: AdaptiveModal?`. Formulários/confirmações continuam na pilha transitória do `ModalManager`, intocado.

- **Alternativa A (rejeitada): `ModalManager` + flag `prefersPane` na classe.** Menos código e call sites inalterados, mas polui o `ModalManager` com preocupações de painel e força modelar single-slot com uma pilha. A consistência com o precedente `ChromeController` e a honestidade do modelo de dados pesaram mais.
- **Alternativa B (rejeitada): `ModalManager` + verbo na chamada (`showDetail`).** Mesmo problema de responsabilidade dupla; o intent já está no nome da factory (`viewXModal`).

### Decisão 2 — A **chamada** escolhe detalhe-vs-modal; a **largura** escolhe painel-vs-sheet

O eixo que a feature controla é **qual mecanismo** (detalhe vs modal), não **qual superfície** (painel vs sheet). Superfície é largura, resolvida dentro da casca — mantendo features agnósticas de layout, como no padrão `ChromeEffect`. Os nomes das factories já carregam a semântica: `viewXModal()` é detalhe; `addX()/deleteX()/payX()` são modais. Migração mecânica: os 5 call sites `view*` trocam `modalManager.show(...)` → `detailController.show(...)`.

### Decisão 3 — `AdaptiveModal` separa conteúdo do recipiente

Nova base `AdaptiveModal` em `:core:designsystem` que expõe o **conteúdo puro** do detalhe (`@Composable DetailContent()`), desacoplado do recipiente. Um único `DetailHost` renderiza esse conteúdo nas duas superfícies:
- **estreito** → dentro de um `ModalBottomSheet` (o wrapper de sheet, incluindo insets e `skipPartiallyExpanded`, escrito uma vez no `DetailHost`);
- **largo** → dentro de uma coluna fixa à direita, com header (título + X) e o `DetailContent` rolável.

`AdaptiveModal` mantém `ViewModelStoreOwner` (provendo `LocalViewModelStoreOwner`), então `koinViewModel()` dentro do detalhe resolve para o store escopado ao objeto — idêntico nas duas superfícies. Como é o **mesmo objeto** no `current` do controller, cruzar o breakpoint transforma sheet ⇄ painel sem perder estado (mesma filosofia do commit `4fd3954b`).

### Decisão 4 — O painel vive no `Row` da casca (ramo largo)

O `DetailHost` no modo largo é plugado como **irmão de `content(padding)`** no `Row` do `ChromeHost` (linha ~184), formando `rail | conteúdo | painel`. Fica fora do `AnimatedVisibility` do rail, então o painel é reservado em **todas** as telas largas, independentemente de `ChromeConfig` (o `Row` sempre é montado). No modo estreito, o `DetailHost` renderiza o sheet na camada de overlay (via o mesmo host, montado próximo ao `ModalManagerHost` no `App`), preservando o comportamento atual.

### Decisão 5 — Detalhe sobre detalhe **substitui** (slot único)

`detailController.show(x)` sobrescreve `current`. Sem navegação "voltar" interna. É o comportamento natural de um painel reservado (como clicar em outro item de uma lista mestre-detalhe). Um mini-histórico interno pode ser adicionado depois, se necessário, sem quebrar a API.

### Decisão 6 — Dono do controller e DI

`DetailPaneController` é registrado como `single {}` no Koin. O dono natural é `:core:designsystem` (junto do `ModalManager`, no `designsystemModule`), já que o `DetailHost` e `AdaptiveModal` vivem ali e o controller não depende de nenhuma feature. A casca apenas provê `LocalDetailPaneController` e monta o `DetailHost`.

## Risks / Trade-offs

- **[Dois mecanismos para um dev entender (`ModalManager` vs `DetailPaneController`)]** → A fronteira é nítida e semântica (transitório/empilhável vs detalhe/single-slot) e espelha o `ChromeController` já existente; documentar em `feature/README.md` / `core:designsystem`.
- **[Painel + conteúdo apertados perto do breakpoint de 600dp]** → Ponto aberto (ver abaixo): o painel pode entrar num breakpoint mais alto (Expanded/≥840dp) que o rail (Medium/600dp), reusando/estendendo `WindowSize.kt`.
- **[Empty-state permanente em telas sem itens detalháveis (ex.: report config)]** → Aceitável por ora; o empty-state comunica "selecione um item". Reavaliar se incomodar (poderia esconder o painel em seções sem detalhe).
- **[Coexistência painel + overlay no caso empilhado]** (em tela larga: painel mostra `ViewOperation`, usuário toca "editar" → `modalManager.show(EditTransaction)`) → O overlay é sempre desenhado por cima; as duas camadas convivem sem coordenação especial. Validar z-order e foco.
- **[Regressão em telas estreitas]** → O caminho estreito reusa exatamente o `ModalBottomSheet` atual dentro do `DetailHost`; cobrir os 5 `view*` em teste manual estreito+largo.

## Open Questions

- **Breakpoint do painel:** entra no mesmo Medium/600dp do rail, ou num breakpoint mais alto (Expanded/≥840dp) para evitar conteúdo+painel apertados? (maior impacto visual)
- **Largura do painel:** valor fixo (ex.: 360–400dp) vs fração da largura; comportamento em telas ultra-largas.
- **Telas sem detalhe:** manter empty-state reservado em toda tela larga, ou esconder o painel em seções que nunca populam detalhe?
- **Título/close do painel:** de onde vem o título do header do painel — cada `AdaptiveModal` expõe um `title`, ou o header é responsabilidade do próprio `DetailContent`?
