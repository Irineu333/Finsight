## Context

Existem hoje **duas pilhas de overlay independentes**, criadas de propósito no `adaptive-detail-pane`:

- `ModalManager` (`core/designsystem/.../ui/component/ModalManager.kt:24`) — pilha `mutableStateListOf<Modal>` de modais transitórios (formulários, confirmações). É o que os ViewModels injetam e onde chamam `dismissAll()`.
- `DetailPaneController` (`core/designsystem/.../ui/component/AdaptiveDetail.kt:92`) — slot único `current: AdaptiveModal?` para os 5 detalhes `view*`. Renderizado como painel fixo (largo) ou `ModalBottomSheet` (estreito). **Nunca é injetado em ViewModel**; só acessível na UI via `LocalDetailPaneController`.

O teardown pós-operação está espalhado: ~21 `DeleteXxxViewModel` e os form ViewModels terminam com `modalManager.dismissAll()`. Esse método limpa **apenas** o `ModalManager`.

Além disso, telas de navegação cheia ancoradas numa entidade por id não reagem ao desaparecimento dela. Levantamento:

| Tela | Rota | Observa | Ao excluir a entidade | Volta? |
|------|------|---------|----------------------|--------|
| `InvoiceTransactionsViewModel` | `InvoiceTransactionsRoute(creditCardId)` | `observeCreditCardById(id).filterNotNull()` em `combine` | **congela** (combine trava) | não |
| `SupportIssueViewModel` | `SupportIssueRoute(issueId)` | `observeIssueById(id)` → `null` vira `Loading` | **limbo** (spinner eterno) | não |
| `AccountsViewModel` | `AccountsRoute(accountId?)` | `observeAllAccounts()` (lista) | `.first()` em vazio → **crash** | não |
| `CreditCardsViewModel` | `CreditCardsRoute(creditCardId?)` | `observeAllCreditCards()` (lista) | auto-cura p/ índice 0 / `Empty` | n/a |

Só `InvoiceTransactions` congela; os demais degradam de formas diferentes. Nenhuma tem efeito que navegue de volta quando a entidade-chave some.

## Goals / Non-Goals

**Goals:**
- Fechar o detalhe órfão junto com os overlays transitórios, a partir de **um único ponto** de teardown (bug 2).
- Fazer a tela de faturas **voltar** quando o cartão é excluído, em vez de congelar (bug 1).
- Endurecer os primos da mesma família (crash de contas, limbo de suporte) com o mesmo princípio.

**Non-Goals:**
- Reintroduzir os `view*` no `ModalManager` (a separação de pilhas é intencional e permanece).
- Fazer o detalhe **re-observar** a entidade para atualizar in-place (o one-shot `flow` continua; o teardown fecha em vez de atualizar).
- Auto-fechar o detalhe por edição isoladamente (é consequência aceita do teardown centralizado — ver Decisão 1).
- Alterar assinaturas dos ~21 `DeleteXxxViewModel`/form ViewModels.

## Decisions

### Decisão 1 — Centralizar o fechamento em `dismissAll()` (bug 2)

`ModalManager` passa a compor o `DetailPaneController`; `dismissAll()` limpa a própria pilha **e** chama `detailPaneController.dismiss()`. Semanticamente, "dispensar todos os overlays" passa a incluir o slot de detalhe.

```kotlin
class ModalManager(
    private val detailPaneController: DetailPaneController,
) {
    // ...
    fun dismissAll() {
        modalState.forEach(Modal::onDismissed)
        modalState.clear()
        detailPaneController.dismiss() // no-op quando não há detalhe aberto
    }
}
```
DI: `single { ModalManager(get()) }`.

**Por que centralizado aqui:** `dismissAll()` já é o ponto compartilhado por todos os fluxos de teardown. Injetar o controller e cobrir as duas pilhas ali corrige os ~21 fluxos de delete de uma vez, **sem tocar nenhum ViewModel de feature** e sem expor um controller de UI à camada de ViewModel. O acoplamento é unidirecional (`ModalManager → DetailPaneController`, ambos em `core:designsystem`, sem ciclo).

- **Alternativa A (rejeitada): chamar `detailController.dismiss()` em cada `DeleteXxxViewModel`.** Espalha a responsabilidade por ~21 arquivos e forçaria injetar/expor o `DetailPaneController` (hoje só-UI) na camada de ViewModel — contraria a separação de camadas.
- **Alternativa B (rejeitada): facade `OverlayController` novo que os VMs passam a injetar.** Trocar `ModalManager` por um facade em ~21 VMs é o oposto de "centralizar" e gera churn desnecessário.

**Efeito colateral aceito — edição também fecha o detalhe:** os form ViewModels (ex.: `EditTransactionViewModel:134`) também chamam `dismissAll()`. Com a mudança, salvar uma edição a partir do detalhe fecha o painel. Isso (a) **restaura** o comportamento pré-`adaptive-detail-pane` (quando `view*` + form viviam na mesma pilha e `dismissAll` fechava ambos) e (b) é **melhor que o atual**, pois `ViewOperationViewModel` usa `flow { emit(getOperationById(id) ?: operation) }` **one-shot** — hoje o detalhe fica **stale** após editar. Fechar > exibir obsoleto. (Re-observar o detalhe in-place fica como melhoria futura fora de escopo.)

### Decisão 2 — Reagir ao `null` navegando de volta (bug 1)

`InvoiceTransactionsViewModel` deixa de esconder o `null`. Emite um evento único quando o cartão observado some; a Screen coleta e chama `onNavigateBack()` (já injetado pelo `CreditCardsGraph.kt:43`). Padrão idêntico ao `events`/`Channel.receiveAsFlow()` de `ViewOperationViewModel`.

```kotlin
sealed interface InvoiceTransactionsEvent {
    data object CreditCardDeleted : InvoiceTransactionsEvent
}

private val _events = Channel<InvoiceTransactionsEvent>(Channel.BUFFERED)
val events = _events.receiveAsFlow()

private val creditCardFlow = creditCardRepository
    .observeCreditCardById(creditCardId)
    .onEach { if (it == null) _events.send(InvoiceTransactionsEvent.CreditCardDeleted) }
    .filterNotNull()
```
```kotlin
// InvoiceTransactionsScreen
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) { InvoiceTransactionsEvent.CreditCardDeleted -> onNavigateBack() }
    }
}
```
Manter o `filterNotNull()` após o `onEach` é aceitável: o `combine` congela no último valor bom, mas a tela já está saindo — sem flash de UI inválida. O evento (não estado) evita re-disparo em recomposição.

### Decisão 3 — Endurecer os primos com o mesmo princípio

- `AccountsViewModel:51`: trocar `accounts.getOrNull(index) ?: accounts.first()` por tratamento de lista vazia (estado `Empty` já existe no padrão de `CreditCardsViewModel`, ou voltar). Elimina o `NoSuchElementException`.
- `SupportIssueViewModel`: quando `observeIssueById` emitir `null` após ter carregado, emitir evento de voltar (mesmo padrão da Decisão 2), em vez de ficar em `Loading` eterno.

Escopo secundário: são a "família" que o levantamento de bugs parecidos revelou; ficam no mesmo change por coesão, mas são independentes dos bugs 1/2 e podem ser cortados sem afetá-los.

## Risks / Trade-offs

- **Teardown mais amplo:** `dismissAll()` agora fecha o detalhe em todos os fluxos que o chamam (delete e save). Mitigado por ser restauração de comportamento histórico e melhora sobre o display stale atual (Decisão 1).
- **`filterNotNull` mantido no bug 1:** o combine ainda congela no último valor; aceitável porque a navegação de volta ocorre no mesmo frame. Alternativa (remover `filterNotNull` e modelar `creditCard?` no state) foi preterida por exigir reescrever o `combine` inteiro sem ganho de UX.

## Migration / Verification

- `./gradlew check` e testes unitários Android verdes.
- Manual: excluir de dentro de cada `view*` (desktop largo e estreito) → detalhe fecha junto com a confirmação.
- Manual: abrir faturas de um cartão e excluí-lo pelo overflow → volta para a lista, sem congelar.
- Manual: editar a partir do detalhe → detalhe fecha (sem stale).
- Manual: excluir todas as contas com a tela de contas aberta → sem crash.
