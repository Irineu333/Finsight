## Why

Ao excluir uma entidade, partes da UI que dependem dela ficam **órfãs** — congeladas, vazias ou mostrando dados fantasma. São dois sintomas do mesmo defeito ("a UI não reage ao desaparecimento da entidade"), com mecanismos distintos:

- **Bug 2 — overlay não fecha:** os `view*` (detalhe) hoje vivem no `DetailPaneController` (painel/sheet de detalhe), uma pilha **separada** do `ModalManager`. Todos os `DeleteXxxViewModel` só chamam `modalManager.dismissAll()`, que **não alcança** o `DetailPaneController`. Resultado: ao excluir de dentro de um detalhe, a confirmação fecha mas o **painel/sheet permanece aberto** exibindo a entidade já excluída. Regressão introduzida pela separação das pilhas no `adaptive-detail-pane`.
- **Bug 1 — tela congela:** `InvoiceTransactionsViewModel` observa o cartão por id com `observeCreditCardById(id).filterNotNull()` dentro de um `combine`. Ao excluir o cartão (inclusive pelo menu overflow da própria tela de faturas), o Flow emite `null`, o `filterNotNull` descarta, e o `combine` **nunca re-emite** — a tela congela mostrando o cartão fantasma, sem voltar. **Este bug é pré-existente**, anterior ao painel de detalhe.

Investigando problemas parecidos (mesma família "tela ancorada numa entidade que some"), encontramos dois primos latentes:
- `AccountsViewModel` (`AccountsViewModel.kt:51`): `accounts.getOrNull(index) ?: accounts.first()` **crasha** (`NoSuchElementException`) se todas as contas forem excluídas com a tela aberta.
- `SupportIssueViewModel`: usa o padrão correto (null → `Loading`, sem congelar), mas fica em **limbo** (spinner eterno) se a issue for excluída, sem voltar.

## What Changes

- **Bug 2 (centralizar o fechamento):** `ModalManager.dismissAll()` passa a desmontar **todos os overlays**, incluindo o slot do `DetailPaneController`. Um único ponto de teardown fecha as duas pilhas; os ~21 `DeleteXxxViewModel` e os form ViewModels **não mudam**. Como `ViewOperationViewModel` (e pares) usa um `flow` one-shot que já deixava o detalhe **stale** após editar, fechar o detalhe no teardown é consistente e ainda elimina o display obsoleto.
- **Bug 1 (corrigir + robustez):** `InvoiceTransactionsViewModel` deixa de engolir o `null`: emite um evento `CreditCardDeleted` quando o cartão observado some; a Screen coleta o evento e chama `onNavigateBack()`. Padrão reativo alinhado ao `events`/`Channel` já usado por `ViewOperationViewModel`.
- **Robustez da mesma família (melhoria):** corrigir o crash de `AccountsViewModel` (lista vazia → estado `Empty`/voltar em vez de `.first()`) e o limbo de `SupportIssueViewModel` (voltar quando a issue observada some), aplicando o mesmo princípio.

## Capabilities

### Modified Capabilities
- `adaptive-detail-pane`: o teardown de overlays (`dismissAll`) passa a incluir o painel/sheet de detalhe do `DetailPaneController`, garantindo que detalhes órfãos sejam dispensados junto com os modais transitórios.
- `navigation`: telas ancoradas numa única entidade por id retornam automaticamente quando essa entidade é excluída, em vez de congelar, crashar ou ficar em limbo.

## Impact

- **`core/designsystem`** — `ModalManager` recebe o `DetailPaneController` por injeção; `dismissAll()` também chama `detailPaneController.dismiss()`. `DesignSystemModule`: `single { ModalManager(get()) }`.
- **`feature/creditcards/impl`** — `InvoiceTransactionsViewModel` (evento `CreditCardDeleted` ao observar `null`) + `InvoiceTransactionsScreen` (coleta o evento → `onNavigateBack()`).
- **`feature/accounts/impl`** — `AccountsViewModel` deixa de chamar `.first()` em lista vazia.
- **`feature/support/impl`** — `SupportIssueViewModel`/`Screen` voltam quando a issue observada some (após ter carregado).
- **Sem novas dependências.** Sem mudanças nos ~21 `DeleteXxxViewModel`/form ViewModels.
