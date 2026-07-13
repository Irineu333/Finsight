## Why

Os modais de **detalhe** (`view*`: operação, ajuste, categoria, orçamento, recorrência) usam `ModalBottomSheet`, que ancora embaixo e estica na largura toda. Em janelas muito largas (desktop) isso vira uma faixa larga e rasa, com conteúdo perdido no centro — uma distorção visual. Precisamos de uma adaptação de layout que, em telas suficientemente largas, exiba essas informações num painel dedicado em vez de um bottom sheet.

## What Changes

- Introduzir um **painel de detalhe** fixo à direita, **sempre reservado** em janelas largas (≥ breakpoint), com **empty-state** quando nada está selecionado e **botão de fechar (X)**.
- Em janelas estreitas, os detalhes continuam sendo exibidos como **bottom sheet**, sem mudança de comportamento.
- A escolha painel-vs-sheet é feita pela **largura da janela** dentro da casca; a feature apenas publica "aqui está um detalhe".
- Novo mecanismo `DetailPaneController` (irmão do `ChromeController`), **separado** do `ModalManager` — que permanece **intacto** para modais transitórios/empilháveis (formulários, confirmações).
- Nova base `AdaptiveModal` que separa o **conteúdo** do detalhe do seu **recipiente**, permitindo renderizar o mesmo conteúdo como sheet ou como painel; os 5 modais `view*` passam a estendê-la.
- A **chamada** decide o mecanismo (detalhe vs modal): os 5 call sites `view*` trocam `modalManager.show(...)` por `detailController.show(...)`. O restante das chamadas de modal permanece inalterado.
- Abrir um detalhe a partir de outro **substitui** o conteúdo do painel (slot único, sem navegação "voltar" interna).
- Cruzar o breakpoint com um detalhe aberto **preserva o estado** (mesmo objeto/`ViewModelStore`): sheet ⇄ painel sem reabrir.

## Capabilities

### New Capabilities
- `adaptive-detail-pane`: apresentação adaptativa por largura de janela dos detalhes `view*` — bottom sheet em telas estreitas, painel fixo reservado à direita (com empty-state e fechar) em telas largas — roteada por um `DetailPaneController` dedicado, distinto da pilha de overlay transitória do `ModalManager`.

### Modified Capabilities
- `feature-entry-points`: os modais de **detalhe** retornados por um entry point passam a ser exibidos via `DetailPaneController` (apresentação adaptativa), não mais via `ModalManager`. As assinaturas de entry point podem retornar o tipo `AdaptiveModal` de `:core:designsystem` (ainda um `Modal` de core), mantendo a regra de que só referenciam tipos de `:core:*`.

## Impact

- **`core/designsystem`**: nova base `AdaptiveModal` (separa `DetailContent()` do recipiente); novo `DetailPaneController` + `LocalDetailPaneController`; novo `DetailHost` (largo → coluna fixa + X + empty-state; estreito → `ModalBottomSheet`). `ModalManager` **não é modificado**. Reutiliza `isWideWindow()` (`WindowSize.kt`).
- **`feature/shell/impl`**: `ChromeHost` pluga o painel no `Row` do ramo largo (irmão de `content(padding)`) e provê o `DetailPaneController`; a casca é o único lugar que decide painel-vs-sheet.
- **5 modais `view*` + entry points + call sites**: `ViewOperationModal`, `ViewAdjustmentModal` (transactions), `ViewCategoryModal` (categories), `ViewBudgetModal` (budgets), `ViewRecurringModal` (recurring) estendem `AdaptiveModal`; suas factories em `*Entry` retornam `AdaptiveModal`; os call sites (incluindo os do dashboard) trocam para `detailController.show(...)`.
- **DI (Koin)**: registro do `DetailPaneController` como singleton no `designsystemModule` (ou no shell, conforme o dono natural do controller).
- **Dependências**: nenhuma nova; usa a API `androidx.compose.material3.adaptive` já presente.
