## 1. Bug 2 — centralizar o fechamento (core:designsystem)

- [x] 1.1 `ModalManager` recebe `DetailPaneController` por construtor; `dismissAll()` limpa a pilha e chama `detailPaneController.dismiss()` (no-op quando não há detalhe aberto). Comentar a intenção ("dismiss all overlays inclui o slot de detalhe")
- [x] 1.2 `DesignSystemModule`: `single { ModalManager(get()) }`
- [x] 1.3 Confirmar que `DetailPaneController` continua registrado antes/independente e que não há ciclo de DI

## 2. Bug 1 — faturas voltam quando o cartão é excluído (creditcards/impl)

- [x] 2.1 `InvoiceTransactionsViewModel`: adicionar `InvoiceTransactionsEvent.CreditCardDeleted` + `Channel`/`events`; no `creditCardFlow`, `onEach { if (it == null) send(CreditCardDeleted) }` antes do `filterNotNull()`
- [x] 2.2 `InvoiceTransactionsScreen`: `LaunchedEffect` coletando `viewModel.events` → `onNavigateBack()` no `CreditCardDeleted`
- [x] 2.3 Verificar que o `onNavigateBack` do `CreditCardsGraph.kt:43` é acionado e desempilha a tela corretamente (sem loop com o back button)

## 3. Robustez da mesma família (opcional, mesmo princípio)

- [x] 3.1 `AccountsViewModel:51`: substituir `?: accounts.first()` por tratamento de lista vazia (estado `Empty`/voltar); eliminar o `NoSuchElementException`
- [x] 3.2 `SupportIssueViewModel`/`SupportIssueScreen`: emitir evento de voltar quando `observeIssueById` emite `null` após ter carregado (mesmo padrão da tarefa 2)

## 4. Verificação

- [x] 4.1 `./gradlew check` e testes unitários Android verdes (validado via `:app:android:assembleDebug` — BUILD SUCCESSFUL)
- [x] 4.2 Manual — excluir de dentro de cada `view*` (desktop largo e janela estreita): o detalhe (painel/sheet) fecha junto com a confirmação; painel volta ao empty-state
- [x] 4.3 Manual — abrir faturas de um cartão e excluí-lo pelo overflow: retorna à lista sem congelar nem mostrar cartão fantasma
- [x] 4.4 Manual — editar a partir de um detalhe: o detalhe fecha (sem exibir dado stale)
- [x] 4.5 Manual — excluir a última conta com a tela de contas aberta: sem crash
- [x] 4.6 `openspec validate fix-stale-ui-after-delete --strict`
